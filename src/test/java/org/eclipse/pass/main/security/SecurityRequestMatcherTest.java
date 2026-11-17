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
package org.eclipse.pass.main.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.eclipse.pass.main.SimpleIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests the request matchers in {@link SecurityConfiguration}: the CSRF rule that applies to GET requests
 * under /doi/ and the Content-Security-Policy header that is only set under /app/.
 */
class SecurityRequestMatcherTest extends SimpleIntegrationTest {
    private static final String CSP_HEADER = "Content-Security-Policy";

    /**
     * GET requests to the DOI service have side effects, so they must be rejected without a CSRF token.
     * Includes variations with a trailing slash since these depend on how the path is matched.
     *
     * @param path request path relative to the base URL
     * @throws IOException if the request fails
     */
    @ParameterizedTest
    @ValueSource(strings = {"doi", "doi/", "doi/journal", "doi/journal/", "doi/manuscript"})
    void doiGetWithoutCsrfTokenIsForbidden(String path) throws IOException {
        try (Response response = get(newOkhttpClient(), path, null)) {
            assertEquals(403, response.code(), "Expected CSRF rejection for GET /" + path);
        }
    }

    /**
     * A GET to the DOI service with a valid CSRF token gets past the CSRF check. An invalid DOI is
     * used so that no external service is called.
     *
     * @throws IOException if the request fails
     */
    @Test
    void doiGetWithCsrfTokenIsNotRejected() throws IOException {
        OkHttpClient client = newOkhttpClient();
        String token = getCsrfToken(client);
        try (Response response = get(client, "doi/journal?doi=moo", token)) {
            assertEquals(400, response.code());
        }
    }

    /**
     * GET requests outside the DOI service do not need a CSRF token. This includes a path that only
     * starts with the same characters as /doi.
     *
     * @param path request path relative to the base URL
     * @throws IOException if the request fails
     */
    @ParameterizedTest
    @ValueSource(strings = {"data/grant", "doiother"})
    void nonDoiGetWithoutCsrfTokenIsNotForbidden(String path) throws IOException {
        try (Response response = get(newOkhttpClient(), path, null)) {
            assertNotEquals(403, response.code(), "Unexpected CSRF rejection for GET /" + path);
        }
    }

    /**
     * The Content-Security-Policy header is set for requests under /app/, whatever the response status.
     *
     * @param path request path relative to the base URL
     * @throws IOException if the request fails
     */
    @ParameterizedTest
    @ValueSource(strings = {"app", "app/", "app/index.html"})
    void cspHeaderIsSetUnderApp(String path) throws IOException {
        try (Response response = get(newOkhttpClient(), path, null)) {
            String csp = response.header(CSP_HEADER);
            assertNotNull(csp, "Expected " + CSP_HEADER + " for GET /" + path);
            assertTrue(csp.contains("default-src"), "Unexpected policy: " + csp);
        }
    }

    /**
     * The Content-Security-Policy header is not set for requests outside /app/, including a path
     * that only starts with the same characters as /app.
     *
     * @param path request path relative to the base URL
     * @throws IOException if the request fails
     */
    @ParameterizedTest
    @ValueSource(strings = {"data/grant", "application", "doi/journal?doi=moo"})
    void cspHeaderIsNotSetOutsideApp(String path) throws IOException {
        try (Response response = get(newOkhttpClient(), path, null)) {
            assertNull(response.header(CSP_HEADER), "Unexpected " + CSP_HEADER + " for GET /" + path);
        }
    }

    private Response get(OkHttpClient client, String path, String csrfToken) throws IOException {
        Request.Builder builder = new Request.Builder().url(getBaseUrl() + path)
            .header("Authorization", BACKEND_CREDENTIALS);
        if (csrfToken != null) {
            builder.header("X-XSRF-TOKEN", csrfToken);
        }
        return client.newCall(builder.get().build()).execute();
    }
}
