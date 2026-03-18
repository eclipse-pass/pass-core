package org.eclipse.pass.doi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.util.Map;

import jakarta.json.JsonObject;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.Test;

/**
 * Unit tests that check the behavior of retrieveMetadata by mocking the external service.
 */
public class ExternalDoiServiceConnectorTest {
    private final OkHttpClient okhttpClient = new OkHttpClient();

    private ExternalDoiService mockService(String baseUrl) {
        return new ExternalDoiService() {
            @Override
            public JsonObject processObject(JsonObject object) {
                return object;
            }

            @Override
            public Map<String, String> parameterMap() {
                return Map.of();
            }

            @Override
            public String name() {
                return "test";
            }

            @Override
            public Map<String, String> headerMap() {
                return Map.of();
            }

            @Override
            public String baseUrl() {
                return baseUrl;
            }
        };
    }

    @Test
    void testRetrieveMetadataNotFound() throws IOException {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(404));

            ExternalDoiServiceConnector underTest = new ExternalDoiServiceConnector(okhttpClient);
            ExternalDoiService service = mockService(server.url("/").toString());

            JsonObject result = underTest.retrieveMetadata("10.123/abc", service);

            assertNotNull(result);
            assertEquals(404, result.getInt(ExternalDoiServiceConnector.HTTP_STATUS_CODE));
        }
    }

    @Test
    void testRetrieveMetadataBadIO() throws IOException {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));

            ExternalDoiServiceConnector underTest = new ExternalDoiServiceConnector(okhttpClient);
            ExternalDoiService service = mockService(server.url("/").toString());

            JsonObject result = underTest.retrieveMetadata("10.123/abc", service);

            assertNull(result);
        }
    }

    @Test
    void testRetrieveMetadataBadJson() throws IOException {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("This is not JSON"));

            ExternalDoiServiceConnector underTest = new ExternalDoiServiceConnector(okhttpClient);
            ExternalDoiService service = mockService(server.url("/").toString());

            JsonObject result = underTest.retrieveMetadata("10.123/abc", service);

            assertNull(result);
        }
    }

    @Test
    void testRetrieveMetadataSuccess() throws IOException {
        try (MockWebServer server = new MockWebServer()) {
            String json = "{\"foo\":\"bar\"}";
            server.enqueue(new MockResponse().setBody(json));

            ExternalDoiServiceConnector underTest = new ExternalDoiServiceConnector(new OkHttpClient());
            ExternalDoiService service = mockService(server.url("/").toString());

            JsonObject result = underTest.retrieveMetadata("10.4137/cmc.s38446", service);

            assertNotNull(result);
            assertEquals("bar", result.getString("foo"));
        }
    }
}
