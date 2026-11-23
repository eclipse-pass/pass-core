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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.eclipse.pass.main.SamlIntegrationTest;
import org.eclipse.pass.main.TestSamlIdp;
import org.eclipse.pass.object.model.User;
import org.junit.jupiter.api.Test;

/**
 * Test the SAML login process itself.
 */
public class SamlLoginTest extends SamlIntegrationTest {
    private Response get_grants() throws IOException {
        Request request = new Request.Builder().url(getBaseUrl() + "data/grant")
                .header("Accept", JSON_API_CONTENT_TYPE).get().build();

        return client.newCall(request).execute();
    }

    @Test
    public void testLoginAsDifferentUsers() throws IOException {
        User user1 = doSamlLogin(TestSamlIdp.USER1);

        try (Response response = get_grants()) {
            assertEquals(200, response.code());
        }

        setupClient();
        User user2 = doSamlLogin(TestSamlIdp.USER2);

        try (Response response = get_grants()) {
            assertEquals(200, response.code());
        }

        assertEquals("sallysubmitter123456789@johnshopkins.edu", user1.getUsername());
        assertEquals("thomassubmitter987654321@johnshopkins.edu", user2.getUsername());
    }

    /**
     * A browser asking for a protected resource must be redirected to the IDP with
     * an authentication request that forces the user to authenticate.
     */
    @Test
    public void testBrowserRedirectedToIdp() throws IOException {
        OkHttpClient no_redirect_client = client.newBuilder().followRedirects(false).build();

        HttpUrl url = HttpUrl.get(getBaseUrl() + "data/grant");
        HttpUrl idp_url = null;

        for (int i = 0; i < 5 && idp_url == null; i++) {
            Request request = new Request.Builder().url(url).header("Accept", "text/html").get().build();

            try (Response response = no_redirect_client.newCall(request).execute()) {
                assertTrue(response.isRedirect(), "Expected redirect from " + url);

                url = url.resolve(response.header("Location"));
                assertNotNull(url);
            }

            if (url.toString().startsWith(TestSamlIdp.SSO_URL)) {
                idp_url = url;
            }
        }

        assertNotNull(idp_url, "Not redirected to IDP");

        String saml_request = idp_url.queryParameter("SAMLRequest");
        assertNotNull(saml_request);

        // The redirect binding deflates the request
        String xml = new String(new InflaterInputStream(new ByteArrayInputStream(
                Base64.getDecoder().decode(saml_request)), new Inflater(true)).readAllBytes(),
                StandardCharsets.UTF_8);

        assertTrue(xml.contains("AuthnRequest"), xml);
        assertTrue(xml.contains("ForceAuthn=\"true\""), xml);
        assertTrue(xml.contains(SP_ID), xml);
        assertTrue(xml.contains("AssertionConsumerServiceURL=\"" + getSpLoginUrl() + "\""), xml);
    }

    @Test
    public void testUnsignedResponseRejected() throws IOException {
        String saml_response = TestSamlIdp.createResponse(getSpLoginUrl(), SP_ID, TestSamlIdp.USER1, false);

        postSamlResponse(saml_response).close();

        try (Response response = get_grants()) {
            assertEquals(401, response.code());
        }
    }

    @Test
    public void testResponseForOtherServiceProviderRejected() throws IOException {
        String saml_response = TestSamlIdp.createResponse(getSpLoginUrl(), "https://other.sp/", TestSamlIdp.USER1);

        postSamlResponse(saml_response).close();

        try (Response response = get_grants()) {
            assertEquals(401, response.code());
        }
    }

    @Test
    public void testResponseMissingRequiredAttributeRejected() throws IOException {
        Map<String, List<String>> attributes = new HashMap<>(TestSamlIdp.USER1);
        attributes.remove(TestSamlIdp.EPPN);

        String saml_response = TestSamlIdp.createResponse(getSpLoginUrl(), SP_ID, attributes);

        // The SAML login succeeds, but the user cannot be mapped to a PASS user.

        try (Response response = postSamlResponse(saml_response)) {
            assertEquals(400, response.code());
        }

        try (Response response = get_grants()) {
            assertEquals(400, response.code());
        }
    }
}
