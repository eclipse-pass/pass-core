/*
 *
 * Copyright 2023 Johns Hopkins University
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package org.eclipse.pass.metadataschema;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * RestController implementation class PassSchemaServiceController. This class handles the web
 * request handling of POST requests from the client. It interacts with the SchemaService class, which handles
 * the business logic of retrieving, sorting, and merging the metadata schemas.
 *
 * @see SchemaService
 */
@RestController
public class PassSchemaServiceController {
    private static final Logger LOG = LoggerFactory.getLogger(PassSchemaServiceController.class);

    private final SchemaService schemaService;

    /**
     * Constructor for PassSchemaServiceController
     * @param schemaService the service responsible for managing schemas
     */
    public PassSchemaServiceController(SchemaService schemaService) {
        this.schemaService = schemaService;
    }

    /**
     * Handle GET requests by invoking the SchemaService to handle the business
     * logic of generating a merged schema from the list of relevant repository
     * schemas to a PASS submission
     *
     * @param entityIds A comma-separated list of repository entity IDs
     * @param mergeSchemaOpt A boolean value indicating whether to merge schemas or return individual schemas
     * @throws IOException if the request cannot be read or schema cannot be merged
     * @return a merged schema in JSON format or a set of individual schemas in JSON format
     */
    @GetMapping("/schema")
    public ResponseEntity<?> getSchema(@RequestParam("entityIds") String entityIds,
                                       @RequestParam("merge") String mergeSchemaOpt) throws IOException {
        if (entityIds == null || entityIds.isEmpty()) {
            LOG.error("No entityIds provided");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("No entityIds provided");
        }
        if (mergeSchemaOpt == null || mergeSchemaOpt.isEmpty()) {
            mergeSchemaOpt = "false";
        }
        List<String> repository_list = Arrays.asList(entityIds.split(","));

        ObjectMapper objectMapper = new ObjectMapper();
        ArrayNode responseArray = objectMapper.createArrayNode();

        //front-end will first attempt to merge schemas, if that fails, it will attempt to retrieve individual schemas
        if (mergeSchemaOpt.equalsIgnoreCase("true")) {
            try {
                JsonNode mergedSchema = schemaService.getMergedSchema(repository_list);
                responseArray.add(mergedSchema);
            } catch (IllegalArgumentException | IOException e) {
                LOG.error("Failed to parse schemas", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to parse schemas");
            } catch (MergeFailException e) {
                LOG.error("Failed to merge schemas", e);
                return ResponseEntity.status(HttpStatus.CONFLICT).body("Failed to merge schemas");
            }
        } else {
            List<JsonNode> individual_schemas;
            try {
                individual_schemas = schemaService.getIndividualSchemas(repository_list);
                for (JsonNode schema : individual_schemas) {
                    responseArray.add(schema);
                }
            } catch (IllegalArgumentException | URISyntaxException | IOException e) {
                LOG.error("Failed to retrieve individual schemas", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Failed to retrieve individual schemas");
            }
        }

        String jsonResponse = objectMapper.writeValueAsString(responseArray);
        HttpHeaders headers = new HttpHeaders();
        //APPLICATION_JSON_UTF8 is deprecated and APPLICATION_JSON is preferred, will be interpreted as UTF-8
        headers.setContentType(MediaType.APPLICATION_JSON);
        return ResponseEntity.ok().headers(headers).body(jsonResponse);
    }

}
