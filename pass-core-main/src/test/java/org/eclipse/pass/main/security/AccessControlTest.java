/*
 * Copyright 2023 Johns Hopkins University
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
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.util.List;

import okhttp3.Cookie;
import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.eclipse.pass.main.SamlIntegrationTest;
import org.eclipse.pass.object.PassClient;
import org.eclipse.pass.object.model.Grant;
import org.eclipse.pass.object.model.Source;
import org.eclipse.pass.object.model.Submission;
import org.eclipse.pass.object.model.User;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/**
 * Ensure that HTTP requests are authenticated and authorized appropriately.
 */
public class AccessControlTest extends SamlIntegrationTest {

    // Check the HTTP response code and try to return the JSON result
    private JSONObject check(Response response, int code) throws IOException {
        if (response.code() != code) {
            print(response);
        }

        assertEquals(code, response.code());

        if (response.isSuccessful() && response.code() != 204) {
            String json = response.body().string();

            try {
                return new JSONObject(json);
            } catch (JSONException e) {
                fail("Expected JSON object, got: " + json);
            }
        }

        return null;
    }

    // Construct a PASS object with an optional attribute and optional relationship
    private JSONObject pass_object(String type, Object id) throws JSONException {
        JSONObject result = new JSONObject();
        JSONObject data = new JSONObject();

        data.put("type", type);

        if (id != null) {
            data.put("id", id.toString());
        }

        result.put("data", data);
        data.put("attributes", new JSONObject());
        data.put("relationships", new JSONObject());

        return result;
    }

    private JSONObject pass_object(String type) throws JSONException {
        return pass_object(type, null);
    }

    private void set_attribute(JSONObject obj, String field, Object value) throws JSONException {
        obj.getJSONObject("data").getJSONObject("attributes").put(field, value);
    }

    private String get_id(JSONObject obj) throws JSONException {
        return obj.getJSONObject("data").getString("id");
    }

    private void set_relationship(JSONObject obj, String rel_name, String rel_type, String rel_target)
            throws JSONException {
        JSONObject rels = obj.getJSONObject("data").getJSONObject("relationships");

        JSONObject rel = new JSONObject();

        rels.put(rel_name, rel);
        JSONObject rel_data = new JSONObject();

        rel.put("data", rel_data);
        rel_data.put("id", rel_target);
        rel_data.put("type", rel_type);
    }

    private void print(Response response) throws IOException {
        System.err.println(response.code() + " " + response.message());
        response.headers().names().forEach(h -> System.err.println("  " + h + ": " + response.header(h)));
        System.err.println(response.body().string());
    }

    @Test
    public void testReadGrantsAsAnonymous() throws IOException {
        String url = getBaseUrl() + "data/grant";

        Request request = new Request.Builder().url(url).header("Accept", JSON_API_CONTENT_TYPE)
                .header("Content-Type", JSON_API_CONTENT_TYPE).get().build();

        Response response = client.newCall(request).execute();

        check(response, 401);
    }

    @Test
    public void testReadGrantsAsBackend() throws IOException {
        String url = getBaseUrl() + "data/grant";

        Request request = new Request.Builder().url(url).header("Accept", JSON_API_CONTENT_TYPE)
                .header("Content-Type", JSON_API_CONTENT_TYPE)
                .header("Authorization", BACKEND_CREDENTIALS).get().build();

        Response response = client.newCall(request).execute();

        check(response, 200);
    }

    @Test
    public void testReadPublicationsAsInvalidBackend() throws IOException {
        String url = getBaseUrl() + "data/publication";

        String credentials = Credentials.basic("baduser", "badpassword");

        Request request = new Request.Builder().url(url).header("Accept", JSON_API_CONTENT_TYPE)
                .header("Content-Type", JSON_API_CONTENT_TYPE).header("Authorization", credentials).get().build();

        Response response = client.newCall(request).execute();

        check(response, 401);
    }

    @Test
    public void testReadGrantsAsShibUser() throws IOException {
        String url = getBaseUrl() + "data/grant";

        doSamlLogin();

        Request.Builder builder = new Request.Builder();
        Request request = builder.url(url).header("Accept", JSON_API_CONTENT_TYPE)
                .header("Content-Type", JSON_API_CONTENT_TYPE).get().build();

        Response response = client.newCall(request).execute();

        check(response, 200);
    }

    @Test
    public void testCreateGrantAsShibUser() throws IOException, JSONException {
        String url = getBaseUrl() + "data/grant";
        JSONObject grant = pass_object("grant");
        set_attribute(grant, "projectName", "This is a test");

        doSamlLogin();

        RequestBody body = RequestBody.create(grant.toString(), JSON_API_MEDIA_TYPE);
        Request.Builder builder = new Request.Builder();

        Request request = builder.url(url).header("Accept", JSON_API_CONTENT_TYPE)
                .header("Content-Type", JSON_API_CONTENT_TYPE)
                .header("X-XSRF-TOKEN", getCsrfToken())
                .post(body).build();

        Response response = client.newCall(request).execute();

        check(response, 403);
    }

    @Test
    public void testCreateUpdateDeleteSubmissionAsShibUser() throws IOException, JSONException {
        User submitter = doSamlLogin();

        JSONObject sub = pass_object("submission");
        set_attribute(sub, "submitterName", "Person Personson");
        set_relationship(sub, "submitter", "user", submitter.getId().toString());

        {
            String url = getBaseUrl() + "data/submission";

            RequestBody body = RequestBody.create(sub.toString(), JSON_API_MEDIA_TYPE);
            Request.Builder builder = new Request.Builder();

            Request request = builder.url(url).header("Accept", JSON_API_CONTENT_TYPE)
                    .header("Content-Type", JSON_API_CONTENT_TYPE)
                    .header("X-XSRF-TOKEN", getCsrfToken())
                    .post(body).build();

            Response response = client.newCall(request).execute();

            sub = check(response, 201);
        }

        {
            set_attribute(sub, "submitterName", "Major Major");

            String url = getBaseUrl() + "data/submission/" + get_id(sub);
            RequestBody body = RequestBody.create(sub.toString(), JSON_API_MEDIA_TYPE);
            Request.Builder builder = new Request.Builder();

            Request request = builder.url(url).header("Accept", JSON_API_CONTENT_TYPE)
                    .header("Content-Type", JSON_API_CONTENT_TYPE)
                    .header("X-XSRF-TOKEN", getCsrfToken())
                    .patch(body).build();

            Response response = client.newCall(request).execute();

            check(response, 200);
        }

        {
            String url = getBaseUrl() + "data/submission/" + get_id(sub);
            Request.Builder builder = new Request.Builder();

            Request request = builder.url(url).header("Accept", JSON_API_CONTENT_TYPE)
                    .header("X-XSRF-TOKEN", getCsrfToken())
                    .delete().build();

            Response response = client.newCall(request).execute();

            check(response, 204);
        }
    }

    @Test
    public void testCreateUpdateDeleteSubmissionAsShibUserWithBadCsrfToken() throws IOException, JSONException {
        doSamlLogin();

        {
            String url = getBaseUrl() + "data/submission";

            RequestBody body = RequestBody.create("{}", JSON_API_MEDIA_TYPE);
            Request.Builder builder = new Request.Builder();

            Request request = builder.url(url).header("Accept", JSON_API_CONTENT_TYPE)
                    .header("Content-Type", JSON_API_CONTENT_TYPE)
                    .header("X-XSRF-TOKEN", "badtoken")
                    .post(body).build();

            Response response = client.newCall(request).execute();

            check(response, 403);
        }

        {
            String url = getBaseUrl() + "data/submission/1";
            RequestBody body = RequestBody.create("{}", JSON_API_MEDIA_TYPE);
            Request.Builder builder = new Request.Builder();

            Request request = builder.url(url).header("Accept", JSON_API_CONTENT_TYPE)
                    .header("Content-Type", JSON_API_CONTENT_TYPE)
                    .header("X-XSRF-TOKEN", "badtoken")
                    .patch(body).build();

            Response response = client.newCall(request).execute();

            check(response, 403);
        }

        {
            String url = getBaseUrl() + "data/submission/1";
            Request.Builder builder = new Request.Builder();

            Request request = builder.url(url).header("Accept", JSON_API_CONTENT_TYPE)
                    .header("X-XSRF-TOKEN", "")
                    .delete().build();

            Response response = client.newCall(request).execute();

            check(response, 403);
        }
    }

    @Test
    public void testCreateUpdateDeletePublicationAsShibUser() throws IOException, JSONException {
        doSamlLogin();

        JSONObject pub = pass_object("publication");
        set_attribute(pub, "title", "This is a title");

        {
            String url = getBaseUrl() + "data/publication";

            RequestBody body = RequestBody.create(pub.toString(), JSON_API_MEDIA_TYPE);
            Request.Builder builder = new Request.Builder();

            Request request = builder.url(url).header("Accept", JSON_API_CONTENT_TYPE)
                    .header("Content-Type", JSON_API_CONTENT_TYPE)
                    .header("X-XSRF-TOKEN", getCsrfToken())
                    .post(body).build();

            Response response = client.newCall(request).execute();

            pub = check(response, 201);
        }

        {
            set_attribute(pub, "title", "updated title");

            String url = getBaseUrl() + "data/publication/" + get_id(pub);
            RequestBody body = RequestBody.create(pub.toString(), JSON_API_MEDIA_TYPE);
            Request.Builder builder = new Request.Builder();

            Request request = builder.url(url).header("Accept", JSON_API_CONTENT_TYPE)
                    .header("Content-Type", JSON_API_CONTENT_TYPE)
                    .header("X-XSRF-TOKEN", getCsrfToken())
                    .patch(body).build();

            Response response = client.newCall(request).execute();

            check(response, 200);
        }

        {
            String url = getBaseUrl() + "data/publication/" + get_id(pub);
            Request.Builder builder = new Request.Builder();

            Request request = builder.url(url).header("Accept", JSON_API_CONTENT_TYPE)
                    .header("X-XSRF-TOKEN", getCsrfToken())
                    .delete().build();

            Response response = client.newCall(request).execute();

            check(response, 204);
        }
    }

    @Test
    public void testCreateUpdateDeleteFileAsShibUserOwningSubmission() throws IOException, JSONException {
        // File is associated with a submission associated with submitter
        // Shib user can create file, update the file, but not delete it.

        User submitter = doSamlLogin();

        JSONObject file = pass_object("file");

        set_attribute(file, "name", "test.pdf");

        Submission sub = new Submission();

        try (PassClient pass_client = PassClient.newInstance(refreshableElide)) {
            sub.setSource(Source.PASS);
            sub.setSubmitter(submitter);

            pass_client.createObject(sub);
        }

        set_relationship(file, "submission", "submission", sub.getId().toString());

        {
            String url = getBaseUrl() + "data/file";

            RequestBody body = RequestBody.create(file.toString(), JSON_API_MEDIA_TYPE);
            Request.Builder builder = new Request.Builder();

            Request request = builder.url(url).header("Accept", JSON_API_CONTENT_TYPE)
                    .header("Content-Type", JSON_API_CONTENT_TYPE)
                    .header("X-XSRF-TOKEN", getCsrfToken())
                    .post(body).build();

            Response response = client.newCall(request).execute();

            file = check(response, 201);
        }

        {
            set_attribute(file, "name", "test2.doc");

            String url = getBaseUrl() + "data/file/" + get_id(file);
            RequestBody body = RequestBody.create(file.toString(), JSON_API_MEDIA_TYPE);
            Request.Builder builder = new Request.Builder();

            Request request = builder.url(url).header("Accept", JSON_API_CONTENT_TYPE)
                    .header("Content-Type", JSON_API_CONTENT_TYPE)
                    .header("X-XSRF-TOKEN", getCsrfToken())
                    .patch(body).build();

            Response response = client.newCall(request).execute();

            check(response, 200);
        }

        {
            String url = getBaseUrl() + "data/file/" + get_id(file);
            Request.Builder builder = new Request.Builder();

            Request request = builder.url(url).header("Accept", JSON_API_CONTENT_TYPE)
                    .header("X-XSRF-TOKEN", getCsrfToken())
                    .delete().build();

            Response response = client.newCall(request).execute();

            check(response, 204);
        }
    }

    @Test
    public void testCreateUpdateDeleteEventAsShibUserOwningSubmission() throws IOException, JSONException {
        // File is associated with a submission associated with submitter
        // Shib user can create event pointing to submission they own, but not update or delete it

        User submitter = doSamlLogin();

        JSONObject event = pass_object("submissionEvent");

        set_attribute(event, "comment", "good submission");

        Submission sub = new Submission();

        try (PassClient pass_client = PassClient.newInstance(refreshableElide)) {
            sub.setSource(Source.PASS);
            sub.setSubmitter(submitter);

            pass_client.createObject(sub);
        }

        set_relationship(event, "submission", "submission", sub.getId().toString());

        {
            String url = getBaseUrl() + "data/submissionEvent";

            RequestBody body = RequestBody.create(event.toString(), JSON_API_MEDIA_TYPE);
            Request.Builder builder = new Request.Builder();
            Request request = builder.url(url).header("Accept", JSON_API_CONTENT_TYPE)
                    .header("Content-Type", JSON_API_CONTENT_TYPE).header("X-XSRF-TOKEN", getCsrfToken())
                    .post(body).build();

            Response response = client.newCall(request).execute();

            event = check(response, 201);
        }

        {
            set_attribute(event, "comment", "hmm");

            String url = getBaseUrl() + "data/submissionEvent/" + get_id(event);
            RequestBody body = RequestBody.create(event.toString(), JSON_API_MEDIA_TYPE);
            Request.Builder builder = new Request.Builder();
            Request request = builder.url(url).header("Accept", JSON_API_CONTENT_TYPE)
                    .header("Content-Type", JSON_API_CONTENT_TYPE).patch(body).build();

            Response response = client.newCall(request).execute();

            check(response, 403);
        }

        {
            String url = getBaseUrl() + "data/submissionEvent/" + get_id(event);
            Request.Builder builder = new Request.Builder();
            Request request = builder.url(url).header("Accept", JSON_API_CONTENT_TYPE).delete().build();

            Response response = client.newCall(request).execute();

            check(response, 403);
        }
    }

    @Test
    public void testCreateFileAsShibUserNotOwningSubmission() throws IOException, JSONException {
        String url = getBaseUrl() + "data/file";

        doSamlLogin();

        JSONObject file = pass_object("file");
        // File does not point to submission user owns
        set_attribute(file, "name", "moo.xml");

        RequestBody body = RequestBody.create(file.toString(), JSON_API_MEDIA_TYPE);
        Request.Builder builder = new Request.Builder();
        Request request = builder.url(url).header("Accept", JSON_API_CONTENT_TYPE)
                .header("Content-Type", JSON_API_CONTENT_TYPE).post(body).build();

        Response response = client.newCall(request).execute();

        check(response, 403);
    }

    @Test
    public void testCreateEventAsShibUserNotOwningSubmission() throws IOException, JSONException {
        String url = getBaseUrl() + "data/submissionEvent";

        doSamlLogin();

        JSONObject event = pass_object("submissionEvent");
        // File does not point to submission user owns
        set_attribute(event, "comment", "This should not work");

        RequestBody body = RequestBody.create(event.toString(), JSON_API_MEDIA_TYPE);
        Request.Builder builder = new Request.Builder();

        Request request = builder.url(url).header("Accept", JSON_API_CONTENT_TYPE)
                .header("Content-Type", JSON_API_CONTENT_TYPE).post(body).build();

        Response response = client.newCall(request).execute();

        check(response, 403);
    }

    @Test
    public void testUpdateGrantAsShibUser() throws IOException, JSONException {
        Long id = null;

        doSamlLogin();

        try (PassClient client = PassClient.newInstance(refreshableElide)) {
            Grant grant = new Grant();
            grant.setAwardNumber("zipededoda");
            client.createObject(grant);

            id = grant.getId();
        }

        JSONObject grant = pass_object("grant", id);
        set_attribute(grant, "projectName", "The best project");

        String url = getBaseUrl() + "data/grant/" + id;
        RequestBody body = RequestBody.create(grant.toString(), JSON_API_MEDIA_TYPE);
        Request.Builder builder = new Request.Builder();

        Request request = builder.url(url).header("Accept", JSON_API_CONTENT_TYPE)
                .header("Content-Type", JSON_API_CONTENT_TYPE).patch(body).build();

        Response response = client.newCall(request).execute();

        check(response, 403);
    }

    @Test
    public void testDeleteGrantAsShibUser() throws IOException {
        Long id = null;

        doSamlLogin();

        try (PassClient client = PassClient.newInstance(refreshableElide)) {
            Grant grant = new Grant();
            grant.setAwardNumber("zipededoda");
            client.createObject(grant);

            id = grant.getId();
        }

        String url = getBaseUrl() + "data/grant/" + id;
        Request.Builder builder = new Request.Builder();

        Request request = builder.url(url).header("Accept", JSON_API_CONTENT_TYPE).delete().build();

        Response response = client.newCall(request).execute();

        check(response, 403);
    }

    @Test
    public void testCreateUpdateDeleteGrantAsBackend() throws IOException, JSONException {
        JSONObject grant = pass_object("grant");
        set_attribute(grant, "projectName", "backend test");

        // Create a grant
        {
            String url = getBaseUrl() + "data/grant";

            RequestBody body = RequestBody.create(grant.toString(), JSON_API_MEDIA_TYPE);
            Request request = new Request.Builder().url(url).header("Accept", JSON_API_CONTENT_TYPE)
                    .header("Content-Type", JSON_API_CONTENT_TYPE).header("Authorization", BACKEND_CREDENTIALS)
                    .header("X-XSRF-TOKEN", getCsrfToken())
                    .post(body).build();

            Response response = client.newCall(request).execute();

            grant = check(response, 201);
        }

        // Update the grant
        {
            set_attribute(grant, "projectName", "backend update");

            String url = getBaseUrl() + "data/grant/" + get_id(grant);
            RequestBody body = RequestBody.create(grant.toString(), JSON_API_MEDIA_TYPE);
            Request request = new Request.Builder().url(url).header("Accept", JSON_API_CONTENT_TYPE)
                    .header("Content-Type", JSON_API_CONTENT_TYPE).header("Authorization", BACKEND_CREDENTIALS)
                    .header("X-XSRF-TOKEN", getCsrfToken())
                    .patch(body)
                    .build();

            Response response = client.newCall(request).execute();

            check(response, 200);
        }

        // Delete the grant
        {
            String url = getBaseUrl() + "data/grant/" + get_id(grant);
            Request request = new Request.Builder().url(url).header("Accept", JSON_API_CONTENT_TYPE)
                    .header("Authorization", BACKEND_CREDENTIALS)
                    .header("X-XSRF-TOKEN", getCsrfToken())
                    .delete().build();

            Response response = client.newCall(request).execute();

            check(response, 204);
        }
    }

    @Test
    public void testCsrfTokenAsBackend() throws IOException, JSONException {
        JSONObject grant = pass_object("grant");
        set_attribute(grant, "projectName", "backend test");

        HttpUrl base_url = HttpUrl.get(getBaseUrl());
        String url = getBaseUrl() + "data/grant";

        // Save our own CSRF token
        assertNull(get_cookie("XSRF-TOKEN"));
        client.cookieJar().saveFromResponse(base_url,
                List.of(Cookie.parse(base_url, "XSRF-TOKEN=moo")));
        assertNotNull(get_cookie("XSRF-TOKEN"));

        // Fails if header does not match cookie
        {
            RequestBody body = RequestBody.create(grant.toString(), JSON_API_MEDIA_TYPE);
            Request request = new Request.Builder().url(url).header("Accept", JSON_API_CONTENT_TYPE)
                    .header("Content-Type", JSON_API_CONTENT_TYPE).header("Authorization", BACKEND_CREDENTIALS)
                    .header("X-XSRF-TOKEN", "badmoo").post(body).build();

            Response response = client.newCall(request).execute();

            check(response, 403);
        }

        // Succeeds if header does match cookie
        {
            RequestBody body = RequestBody.create(grant.toString(), JSON_API_MEDIA_TYPE);
            Request request = new Request.Builder().url(url).header("Accept", JSON_API_CONTENT_TYPE)
                    .header("Content-Type", JSON_API_CONTENT_TYPE).header("Authorization", BACKEND_CREDENTIALS)
                    .header("X-XSRF-TOKEN", "moo").post(body).build();

            Response response = client.newCall(request).execute();

            check(response, 201);
        }
    }

    @Test
    public void testCsrfTokenAsShibUser() throws IOException, JSONException {
        HttpUrl base_url = HttpUrl.get(getBaseUrl());

        doSamlLogin();

        JSONObject pub = pass_object("publication");
        set_attribute(pub, "title", "backend test");

        assertNotEquals("moo", getCsrfToken());

        // Create a grant with returned CSRF token
        {
            String url = getBaseUrl() + "data/publication";

            RequestBody body = RequestBody.create(pub.toString(), JSON_API_MEDIA_TYPE);
            Request request = new Request.Builder().url(url).header("Accept", JSON_API_CONTENT_TYPE)
                    .header("Content-Type", JSON_API_CONTENT_TYPE).header("X-XSRF-TOKEN", getCsrfToken())
                    .post(body).build();

            Response response = client.newCall(request).execute();

            check(response, 201);
        }

        // Save our own CSRF token
        client.cookieJar().saveFromResponse(base_url,
                List.of(Cookie.parse(base_url, "XSRF-TOKEN=moo")));

        assertEquals("moo", getCsrfToken());

        // Create a grant with a made up CSRF token
        {
            String url = getBaseUrl() + "data/publication";

            RequestBody body = RequestBody.create(pub.toString(), JSON_API_MEDIA_TYPE);
            Request request = new Request.Builder().url(url).header("Accept", JSON_API_CONTENT_TYPE)
                    .header("Content-Type", JSON_API_CONTENT_TYPE).header("X-XSRF-TOKEN", getCsrfToken())
                    .post(body).build();

            Response response = client.newCall(request).execute();

            check(response, 201);
        }

        // Fails if header does not match token
        {
            String url = getBaseUrl() + "data/publication";

            RequestBody body = RequestBody.create(pub.toString(), JSON_API_MEDIA_TYPE);
            Request request = new Request.Builder().url(url).header("Accept", JSON_API_CONTENT_TYPE)
                    .header("Content-Type", JSON_API_CONTENT_TYPE).header("X-XSRF-TOKEN", "badmoo")
                    .post(body).build();

            Response response = client.newCall(request).execute();

            check(response, 403);
        }
    }

    // Check handling of /app/ including CSP header.
    @Test
    public void testReadAppAuthorized() throws IOException {
        String url = getBaseUrl() + "app/";
        String index_html;

        doSamlLogin();

        {
            Request request = new Request.Builder().url(url + "index.html").get().build();
            Response response = client.newCall(request).execute();
            index_html = response.body().string();

            assertEquals(200, response.code());
            assertNotNull(response.header("Content-Security-Policy"));
        }

        // /app/ returns index.html

        {
            Request request = new Request.Builder().url(url).get().build();
            Response response = client.newCall(request).execute();

            assertEquals(200, response.code());
            assertNotNull(response.header("Content-Security-Policy"));
            assertEquals(index_html, response.body().string());
        }

        // Files that do not exist under /app/ also return index.html

        {
            Request request = new Request.Builder().url(url + "doesnotexist").get().build();
            Response response = client.newCall(request).execute();

            assertEquals(200, response.code());
            assertNotNull(response.header("Content-Security-Policy"));
            assertEquals(index_html, response.body().string());
        }

        // Files that exist under /app/ can be returned

        {
            Request request = new Request.Builder().url(url + "test.txt").get().build();
            Response response = client.newCall(request).execute();

            assertEquals(200, response.code());
            assertNotNull(response.header("Content-Security-Policy"));
            assertNotEquals(index_html, response.body().string());
        }
    }

    @Test
    public void testReadAppNotAuthorized() throws IOException {
        String url = getBaseUrl() + "app/";

        Request request = new Request.Builder().url(url).get().build();
        Response response = client.newCall(request).execute();

        assertEquals(401, response.code());
    }

    private Cookie get_cookie(String name) {
        return client.cookieJar().loadForRequest(HttpUrl.get(getBaseUrl())).stream().
                filter(c -> c.name().equals(name)).findFirst().orElse(null);
    }

    @Test
    public void testLogout() throws IOException {
        doSamlLogin();
        Cookie ses = get_cookie("JSESSIONID");

        assertNotNull(ses);

        {
            String url = getBaseUrl() + "logout";

            Request request = new Request.Builder().url(url).get().build();
            Response response = client.newCall(request).execute();

            assertEquals(204, response.code());
        }

        // Session cookie deleted
        assertEquals(null, get_cookie("JSESSIONID"));

        {
            String url = getBaseUrl() + "app/";

            Request request = new Request.Builder().url(url).get().build();
            Response response = client.newCall(request).execute();

            assertEquals(401, response.code());
        }
    }
}
