/*
 * Copyright 2022 Johns Hopkins University
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.eclipse.pass.object;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.yahoo.elide.Elide;
import com.yahoo.elide.ElideResponse;
import com.yahoo.elide.ElideSettings;
import com.yahoo.elide.RefreshableElide;
import com.yahoo.elide.core.RequestScope;
import com.yahoo.elide.core.datastore.DataStoreTransaction;
import com.yahoo.elide.core.dictionary.EntityDictionary;
import com.yahoo.elide.core.request.route.Route;
import com.yahoo.elide.core.security.User;
import com.yahoo.elide.core.type.ClassType;
import com.yahoo.elide.datastores.aggregation.QueryEngine;
import com.yahoo.elide.jsonapi.JsonApi;
import com.yahoo.elide.jsonapi.JsonApiMapper;
import com.yahoo.elide.jsonapi.JsonApiRequestScope;
import com.yahoo.elide.jsonapi.JsonApiSettings;
import com.yahoo.elide.jsonapi.models.Data;
import com.yahoo.elide.jsonapi.models.JsonApiDocument;
import com.yahoo.elide.jsonapi.models.Relationship;
import com.yahoo.elide.jsonapi.models.Resource;
import com.yahoo.elide.jsonapi.models.ResourceIdentifier;
import org.eclipse.pass.object.model.PassEntity;
import org.eclipse.pass.object.security.WebSecurityRole;

/**
 * PASS client which uses the HTTP verb methods of the main Elide class.
 * Hooks should be triggered and permissions will be checked.
 * <p>
 * Objects retrieved using this client may not work after the client has been closed.
 * This is because relationships are loaded lazily.
 */
public class ElidePassClient implements PassClient {
    private final ElideSettings settings;
    private final User user;
    private final String api_version;
    private final JsonApi jsonApi;
    private final JsonApiMapper jsonApiMapper;

    /**
     * Constructor for ElidePassClient. Will initialize the Elide instance, User, Elide settings, DataStoreTransaction,
     * and the API version.
     *
     * @param refreshableElide Elide instance to use for the Elide PassClient
     * @param user User to use for the Elide PassClient
     */
    public ElidePassClient(RefreshableElide refreshableElide, User user) {
        Elide elide = refreshableElide.getElide();
        this.settings = elide.getElideSettings();
        this.jsonApi = new JsonApi(elide);
        this.user = user;
        this.api_version = settings.getEntityDictionary().getApiVersions().iterator().next();
        JsonApiSettings jsonApiSettings = elide.getSettings(JsonApiSettings.class);
        this.jsonApiMapper = jsonApiSettings.getJsonApiMapper();
    }

    /**
     * Act as a backend user.
     *
     * @param refreshableElide Elide instance to use for the Elide PassClient
     */
    public ElidePassClient(RefreshableElide refreshableElide) {
        this(refreshableElide, new User(null) {
            @Override
            public String getName() {
                return ElidePassClient.class.getName();
            }

            @Override
            public boolean isInRole(String role) {
                return role.equals(WebSecurityRole.BACKEND.getValue());
            }
        });
    }

    private JsonApiRequestScope get_scope(String path, DataStoreTransaction tx) {
        Route route = getRoute(path, null);
        return JsonApiRequestScope.builder()
            .route(route)
            .user(user)
            .dataStoreTransaction(tx)
            .requestId(UUID.randomUUID())
            .elideSettings(settings)
            .build();
    }

    private String get_path(Class<?> type, Long id) {
        StringBuilder result = new StringBuilder();

        result.append('/');
        result.append(EntityDictionary.getEntityName(ClassType.of(type)));

        if (id != null) {
            result.append('/');
            result.append(id);
        }

        return result.toString();
    }

    private JsonApiDocument to_json_api_doc(PassEntity obj) {
        EntityDictionary dict = settings.getEntityDictionary();

        String typeName = EntityDictionary.getEntityName(ClassType.of(obj.getClass()));

        Resource resource = new Resource(typeName, obj.getId() == null ? "-1" : obj.getId().toString());

        Map<String, Relationship> relationships = new HashMap<>();

        for (String name : dict.getRelationships(obj)) {
            Object value = dict.getValue(obj, name, null);
            Data<Resource> data;

            if (value == null) {
                data = new Data<>((Resource) null);
            } else if (value instanceof List) {
                List<Resource> targets = new ArrayList<>();

                for (Object o : List.class.cast(value)) {
                    PassEntity entity = PassEntity.class.cast(o);
                    String target_type = EntityDictionary.getEntityName(ClassType.of(entity.getClass()));
                    String target_id = entity.getId().toString();
                    ResourceIdentifier target = new ResourceIdentifier(target_type, target_id);

                    targets.add(target.castToResource());
                }

                data = new Data<>(targets);
            } else if (value instanceof PassEntity) {
                PassEntity entity = PassEntity.class.cast(value);
                String target_type = EntityDictionary.getEntityName(ClassType.of(entity.getClass()));
                String target_id = entity.getId().toString();
                ResourceIdentifier target = new ResourceIdentifier(target_type, target_id);
                data = new Data<>(target.castToResource());
            } else {
                throw new RuntimeException("Unknown relationship target: " + value);
            }

            relationships.put(name, new Relationship(null, data));
        }

        resource.setRelationships(relationships);

        Map<String, Object> attributes = new HashMap<>();

        for (String name : dict.getAttributes(obj)) {
            attributes.put(name, dict.getValue(obj, name, null));
        }

        resource.setAttributes(attributes);

        return new JsonApiDocument(new Data<>(resource));
    }

    @Override
    public <T extends PassEntity> void createObject(T obj) throws IOException {
        String path = get_path(obj.getClass(), null);

        String json = jsonApiMapper.writeJsonApiDocument(to_json_api_doc(obj));
        Route route = getRoute(path, null);
        ElideResponse<String> response = jsonApi.post(route, json, user, UUID.randomUUID());

        if (response.getStatus() != 201) {
            throw new IOException("Failed to create object: " + response.getStatus() + " " + response.getBody());
        }

        String id = jsonApiMapper.readJsonApiDocument(response.getBody()).getData().getSingleValue().getId();
        settings.getEntityDictionary().setId(obj, id);
        setVersionIfNeeded(response, obj);
    }

    @Override
    public <T extends PassEntity> void updateObject(T obj) throws IOException {
        String path = get_path(obj.getClass(), obj.getId());

        String json = jsonApiMapper.writeJsonApiDocument(to_json_api_doc(obj));
        Route route = getRoute(path, null);
        ElideResponse<String> response = jsonApi.patch(route, json, user, UUID.randomUUID());

        int code = response.getStatus();

        if (code < 200 || code > 204) {
            throw new IOException("Failed to update object: " + code + " " + response.getBody());
        }

        setVersionIfNeeded(response, obj);
    }

    private <T extends PassEntity> void setVersionIfNeeded(ElideResponse<String> response, T obj) throws IOException {
        Object version = jsonApiMapper.readJsonApiDocument(response.getBody()).getData().getSingleValue()
            .getAttributes().get("version");
        if (Objects.nonNull(version)) {
            settings.getEntityDictionary().setValue(obj, "version", version);
        }
    }

    @Override
    public <T extends PassEntity> T getObject(Class<T> type, Long id) throws IOException {
        String path = get_path(type, id);
        Route route = getRoute(path, null);

        ElideResponse<String> response = jsonApi.get(route, user, UUID.randomUUID());

        if (response.getStatus() == 404) {
            return null;
        }

        if (response.getStatus() != 200) {
            throw new IOException("Failed to get object: " + response.getStatus() + " " + response.getBody());
        }

        JsonApiDocument doc = jsonApiMapper.readJsonApiDocument(response.getBody());

        try (DataStoreTransaction tx = jsonApi.getElide().getDataStore().beginReadTransaction()) {
            RequestScope scope = get_scope(path, tx);
            return type.cast(doc.getData().getSingleValue().toPersistentResource(scope).getObject());
        }
    }

    @Override
    public <T extends PassEntity> void deleteObject(Class<T> type, Long id) throws IOException {
        String path = get_path(type, id);
        Route route = getRoute(path, null);
        ElideResponse<String> response = jsonApi.delete(route, "", user, UUID.randomUUID());

        if (response.getStatus() != 204) {
            throw new IOException("Failed to delete object: " + response.getStatus() + " " + response.getBody());
        }
    }

    @Override
    public <T extends PassEntity> PassClientResult<T> selectObjects(PassClientSelector<T> selector) throws IOException {
        Map<String, List<String>> params = new LinkedHashMap<>();
        if (selector.getFilter() != null) {
            PassClient.addParam(params, "filter", selector.getFilter());
        }
        if (selector.getSorting() != null) {
            PassClient.addParam(params, "sort", selector.getSorting());
        }
        PassClient.addParam(params, "page[offset]", String.valueOf(selector.getOffset()));
        PassClient.addParam(params, "page[limit]",  String.valueOf(selector.getLimit()));
        PassClient.addParam(params, "page[totals]", null);

        String path = get_path(selector.getType(), null);
        Route route = getRoute(path, params);

        ElideResponse<String> response = jsonApi.get(route, user, UUID.randomUUID());

        if (response.getStatus() == 404) {
            return null;
        }

        if (response.getStatus() != 200) {
            throw new IOException("Failed to get object: " + response.getStatus() + " " + response.getBody());
        }

        JsonApiDocument doc = jsonApiMapper.readJsonApiDocument(response.getBody());

        Object totalval = doc.getMeta().getValue("page", Map.class).get("totalRecords");
        long total = -1;

        if (totalval != null) {
            total = Long.parseLong(totalval.toString());
        }

        PassClientResult<T> result = new PassClientResult<>(total);

        try (DataStoreTransaction tx = jsonApi.getElide().getDataStore().beginReadTransaction()) {
            RequestScope scope = get_scope(path, tx);

            doc.getData().get().forEach(r -> {
                @SuppressWarnings("unchecked")
                T o = (T) r.toPersistentResource(scope).getObject();
                result.getObjects().add(o);
            });
        }

        return result;
    }

    private Route getRoute(String path, Map<String, List<String>> parameters) {
        Route.RouteBuilder builder = Route.builder()
            .baseUrl(settings.getBaseUrl())
            .path(path)
            .apiVersion(api_version);

        return Objects.nonNull(parameters)
            ? builder.parameters(parameters).build()
            : builder.build();
    }

    @Override
    public void close() throws IOException {
        // no-op
    }
}