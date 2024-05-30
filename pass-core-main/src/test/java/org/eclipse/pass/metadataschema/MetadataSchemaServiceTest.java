package org.eclipse.pass.metadataschema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.yahoo.elide.RefreshableElide;
import okhttp3.Call;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.eclipse.pass.main.SimpleIntegrationTest;
import org.eclipse.pass.object.PassClient;
import org.eclipse.pass.object.model.IntegrationType;
import org.eclipse.pass.object.model.Repository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

public class MetadataSchemaServiceTest extends SimpleIntegrationTest {
    private final String credentials = Credentials.basic(BACKEND_USER, BACKEND_PASSWORD);
    private final OkHttpClient httpClient = new OkHttpClient();
    private Long repo1Id;
    private Long repo2Id;
    private Long repo3Id;
    private Long repo4Id;
    private Long repo5Id;
    private Long repo6Id;

    @Autowired
    protected RefreshableElide refreshableElide;

    @BeforeAll
    public void setupRepos() throws IOException {
        repo1Id = setupRepo1();
        repo2Id = setupRepo2();
        repo3Id = setupRepo3(); //contains missing schema to test error handling
        repo4Id = setupRepo4(); //contains bad schema to test error handling
        repo5Id = setupRepo5(); //contains schemas with merge conflict to test error handling
        repo6Id = setupRepo6();
    }

    @Test
    public void testSchemaControllerOneRepoWithMergeTrue() throws Exception {
        String url = getBaseUrl() + "schema?entityIds=" + repo6Id.toString() + "&merge=true";
        Request okHttpRequest = new Request.Builder()
                .url(url).header("Authorization", credentials)
                .build();
        Call call = httpClient.newCall(okHttpRequest);
        Response response = call.execute();

        assertThat(response.code()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.body()).isNotNull();
        InputStream expected_schema_json = MetadataSchemaServiceTest.class
            .getResourceAsStream("/schemas/jhu/expected_jscholarship_common_merge.json");
        ObjectMapper map = new ObjectMapper();
        JsonNode expected = map.readTree(expected_schema_json);
        JsonNode actual = map.readTree(response.body().string());
        assertEquals(expected, actual);
    }

    @Test
    public void testSchemaControllerOneRepoWithMergeFalse() throws Exception {
        String url = getBaseUrl() + "schema?entityIds=" + repo1Id.toString() + "&merge=false";
        Request okHttpRequest = new Request.Builder()
                .url(url).header("Authorization", credentials)
                .build();
        Call call = httpClient.newCall(okHttpRequest);
        Response response = call.execute();

        assertThat(response.code()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.body()).isNotNull();
        InputStream expected_schema1_json = MetadataSchemaServiceTest.class
            .getResourceAsStream("/schemas/jhu/schema1.json");
        InputStream expected_schema2_json = MetadataSchemaServiceTest.class
            .getResourceAsStream("/schemas/jhu/schema2.json");
        InputStream expected_schema3_json = MetadataSchemaServiceTest.class
            .getResourceAsStream("/schemas/jhu/schema3.json");
        ObjectMapper map = new ObjectMapper();
        JsonNode expected1 = map.readTree(expected_schema1_json);
        JsonNode expected2 = map.readTree(expected_schema2_json);
        JsonNode expected3 = map.readTree(expected_schema3_json);
        ArrayNode expected_array = map.createArrayNode();
        expected_array.add(expected1);
        expected_array.add(expected2);
        expected_array.add(expected3);
        JsonNode actual = map.readTree(response.body().string());
        assertEquals(expected_array, actual);
    }

    @Test
    public void testSchemaControllerTwoRepoWithMergeTrue() throws Exception {
        String url = getBaseUrl() + "schema?entityIds=" + repo1Id.toString() + ","
                + repo2Id.toString() + "&merge=true";
        Request okHttpRequest = new Request.Builder()
                .url(url).header("Authorization", credentials)
                .build();
        Call call = httpClient.newCall(okHttpRequest);
        Response response = call.execute();

        assertThat(response.code()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.body()).isNotNull();
        InputStream expected_schema_json = MetadataSchemaServiceTest.class
            .getResourceAsStream("/schemas/jhu/example_merged_dereferenced.json");
        ObjectMapper map = new ObjectMapper();
        JsonNode expected = map.readTree(expected_schema_json);
        ArrayNode expected_array = map.createArrayNode();
        expected_array.add(expected);
        JsonNode actual = map.readTree(response.body().string());
        assertEquals(expected_array, actual);
    }

    @Test
    public void testSchemaControllerTwoRepoWithMergeFalse() throws Exception {
        String url = getBaseUrl() + "schema?entityIds=" + repo1Id.toString() + ","
                + repo2Id.toString() + "&merge=false";
        Request okHttpRequest = new Request.Builder()
                .url(url).header("Authorization", credentials)
                .build();
        Call call = httpClient.newCall(okHttpRequest);
        Response response = call.execute();

        assertThat(response.code()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.body()).isNotNull();
        InputStream expected_schema_json1 = MetadataSchemaServiceTest.class
            .getResourceAsStream("/schemas/jhu/schema1.json");
        InputStream expected_schema_json2 = MetadataSchemaServiceTest.class
            .getResourceAsStream("/schemas/jhu/schema2.json");
        InputStream expected_schema_json3 = MetadataSchemaServiceTest.class
            .getResourceAsStream("/schemas/jhu/schema3.json");
        InputStream expected_schema_json4 = MetadataSchemaServiceTest.class
            .getResourceAsStream("/schemas/jhu/schema4.json");
        InputStream expected_schema_json5 = MetadataSchemaServiceTest.class
            .getResourceAsStream("/schemas/jhu/schema_to_deref_expected.json");
        ObjectMapper map = new ObjectMapper();
        JsonNode expected1 = map.readTree(expected_schema_json1);
        JsonNode expected2 = map.readTree(expected_schema_json2);
        JsonNode expected3 = map.readTree(expected_schema_json3);
        JsonNode expected4 = map.readTree(expected_schema_json4);
        JsonNode expected5 = map.readTree(expected_schema_json5);
        ArrayNode expected_array = map.createArrayNode();
        expected_array.add(expected1);
        expected_array.add(expected2);
        expected_array.add(expected3);
        expected_array.add(expected4);
        expected_array.add(expected5);
        JsonNode actual = map.readTree(response.body().string());
        assertEquals(expected_array, actual);
    }

    @Test
    public void testSchemaControllerWithNoEntityId() throws Exception {
        String url = getBaseUrl() + "schema?entityIds=&merge=true";
        Request okHttpRequest = new Request.Builder()
                .url(url).header("Authorization", credentials)
                .build();
        Call call = httpClient.newCall(okHttpRequest);
        Response response = call.execute();

        assertThat(response.code()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(response.body()).isNotNull();
    }

    @Test
    public void testSchemaControllerWithMissingLocalSchema() throws Exception {
        String url = getBaseUrl() + "schema?entityIds=" + repo3Id.toString() + "&merge=true";
        Request okHttpRequest = new Request.Builder()
                .url(url).header("Authorization", credentials)
                .build();
        Call call = httpClient.newCall(okHttpRequest);
        Response response = call.execute();

        assertThat(response.code()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(response.body()).isNotNull();
    }

    @Test
    public void testSchemaControllerWithBadLocalSchema() throws Exception {
        String url = getBaseUrl() + "schema?entityIds=" + repo4Id.toString() + "&merge=true";
        Request okHttpRequest = new Request.Builder()
                .url(url).header("Authorization", credentials)
                .build();
        Call call = httpClient.newCall(okHttpRequest);
        Response response = call.execute();

        assertThat(response.code()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(response.body()).isNotNull();
    }

    @Test
    public void testSchemaControllerWithMergeConflict() throws Exception {
        String url = getBaseUrl() + "schema?entityIds=" + repo5Id.toString() + "&merge=true";
        Request okHttpRequest = new Request.Builder()
                .url(url).header("Authorization", credentials)
                .build();
        Call call = httpClient.newCall(okHttpRequest);
        Response response = call.execute();

        assertThat(response.code()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(response.body()).isNotNull();
    }

    private Long setupRepo1() throws IOException {
        List<URI> schemas = Arrays.asList(
            URI.create("https://example.com/metadata-schemas/jhu/schema1.json"),
            URI.create("https://example.com/metadata-schemas/jhu/schema2.json"),
            URI.create("https://example.com/metadata-schemas/jhu/schema3.json"));
        return createRepo("1", IntegrationType.WEB_LINK, schemas, "nih-repository");
    }

    private Long setupRepo2() throws IOException {
        List<URI> schemas = Arrays.asList(
            URI.create("https://example.com/metadata-schemas/jhu/schema3.json"),
            URI.create("https://example.com/metadata-schemas/jhu/schema4.json"),
            URI.create("https://example.com/metadata-schemas/jhu/schema_to_deref.json"));
        return createRepo("2", IntegrationType.WEB_LINK, schemas, "nih-repository");
    }

    private Long setupRepo3() throws IOException {
        List<URI> schemas = Arrays.asList(
            URI.create("https://example.com/metadata-schemas/jhu/schema2.json"),
            URI.create("https://example.com/metadata-schemas/jhu/MissingSchema.json"),
            URI.create("https://example.com/metadata-schemas/jhu/schema_to_deref.json"));
        return createRepo("3", IntegrationType.WEB_LINK, schemas, "nih-repository");
    }

    private Long setupRepo4() throws IOException {
        List<URI> schemas = Arrays.asList(
            URI.create("https://example.com/metadata-schemas/jhu/schema2.json"),
            URI.create("https://example.com/metadata-schemas/jhu/bad_schema.json"),
            URI.create("https://example.com/metadata-schemas/jhu/schema_to_deref.json"));
        return createRepo("4", IntegrationType.WEB_LINK, schemas, "nih-repository");
    }

    private Long setupRepo5() throws IOException {
        List<URI> schemas = Arrays.asList(
            URI.create("https://example.com/metadata-schemas/jhu/schema_merge_conflict1.json"),
            URI.create("https://example.com/metadata-schemas/jhu/schema_merge_conflict2.json"));
        return createRepo("5", IntegrationType.WEB_LINK, schemas, "nih-repository");
    }

    private Long setupRepo6() throws IOException {
        List<URI> schemas = Arrays.asList(
            URI.create("https://example.com/metadata-schemas/jhu/jscholarship.json"),
            URI.create("https://example.com/metadata-schemas/jhu/common.json"));
        return createRepo("6", IntegrationType.FULL, schemas, "j10p-repository");
    }

    private Long createRepo(String repoNum, IntegrationType integrationType, List<URI> schemas, String repoKey)
        throws IOException {
        Repository repository = new Repository();
        repository.setName("Test Repository " + repoNum);
        repository.setDescription("Repository " + repoNum + " - merge conflict schema");
        repository.setUrl(URI.create("https://example.com/repository" + repoNum));
        repository.setAgreementText("Repository " + repoNum + " agreement text");
        repository.setIntegrationType(integrationType);
        repository.setSchemas(schemas);
        repository.setRepositoryKey(repoKey);

        try (PassClient passClient = PassClient.newInstance(refreshableElide)) {
            passClient.createObject(repository);
        }

        return repository.getId();
    }

}
