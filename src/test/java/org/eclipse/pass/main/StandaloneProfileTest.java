/*
 * Copyright 2026 Johns Hopkins University
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
package org.eclipse.pass.main;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.test.context.ActiveProfiles;

/**
 * Check that pass-core runs with only the standalone profile and backend credentials.
 * This is how clients of pass-core run the main docker image for their tests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = Main.class,
    properties = {
        "PASS_CORE_USER=" + IntegrationTestBase.BACKEND_USER,
        "PASS_CORE_PASSWORD=" + IntegrationTestBase.BACKEND_PASSWORD
    })
@ActiveProfiles("standalone")
public class StandaloneProfileTest extends IntegrationTestBase {
    @LocalServerPort
    private int port;

    @Autowired
    private ObjectProvider<RelyingPartyRegistrationRepository> registrations;

    private OkHttpClient client;

    @BeforeEach
    public void setupClient() throws IOException {
        client = newOkhttpClient();
    }

    @Override
    public String getBaseUrl() {
        return "http://localhost:" + port + "/";
    }

    @Test
    public void testSamlNotConfigured() {
        assertNull(registrations.getIfAvailable());
    }

    @Test
    public void testBackendUserCanReadAndCreate() throws IOException {
        String url = getBaseUrl() + "data/publication";

        Request get = new Request.Builder().url(url).header("Accept", JSON_API_CONTENT_TYPE)
                .header("Authorization", BACKEND_CREDENTIALS).get().build();

        try (Response response = client.newCall(get).execute()) {
            assertEquals(200, response.code());
        }

        String json = "{\"data\":{\"type\":\"publication\",\"attributes\":{\"title\":\"standalone\"}}}";

        Request post = new Request.Builder().url(url).header("Accept", JSON_API_CONTENT_TYPE)
                .header("Authorization", BACKEND_CREDENTIALS)
                .header("X-XSRF-TOKEN", getCsrfToken(client))
                .post(RequestBody.create(json, JSON_API_MEDIA_TYPE)).build();

        try (Response response = client.newCall(post).execute()) {
            assertEquals(201, response.code());
        }
    }

    @Test
    public void testUnauthenticatedRequestRejected() throws IOException {
        String url = getBaseUrl() + "data/publication";

        Request json_request = new Request.Builder().url(url).header("Accept", JSON_API_CONTENT_TYPE).get().build();

        try (Response response = client.newCall(json_request).execute()) {
            assertEquals(401, response.code());
        }

        // A browser is not sent to an IDP

        Request html_request = new Request.Builder().url(url).header("Accept", "text/html").get().build();

        try (Response response = client.newCall(html_request).execute()) {
            assertEquals(401, response.code());
            assertNull(response.priorResponse());
        }
    }

    @Test
    public void testSamlLoginUnavailable() throws IOException {
        String saml_response = TestSamlIdp.createResponse(getBaseUrl() + "login/saml2/sso/pass",
                "https://sp.pass/shibboleth", TestSamlIdp.USER1);

        Request login = new Request.Builder().url(getBaseUrl() + "login/saml2/sso/pass")
                .post(new FormBody.Builder().add("SAMLResponse", saml_response).build()).build();

        try (Response response = client.newCall(login).execute()) {
            assertFalse(response.isSuccessful());
        }

        Request get = new Request.Builder().url(getBaseUrl() + "data/publication")
                .header("Accept", JSON_API_CONTENT_TYPE).get().build();

        try (Response response = client.newCall(get).execute()) {
            assertEquals(401, response.code());
        }
    }
}
