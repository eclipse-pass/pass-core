package org.eclipse.pass.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;

import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;
import org.eclipse.pass.main.SamlIntegrationTest;
import org.eclipse.pass.object.PassClient;
import org.eclipse.pass.object.model.Submission;
import org.eclipse.pass.object.model.User;
import org.eclipse.pass.usertoken.Token;
import org.eclipse.pass.usertoken.TokenFactory;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class UserServiceTest extends SamlIntegrationTest {
    @Autowired
    private TokenFactory userTokenFactory;

    @Test
    public void testHandleRequest() throws IOException, JSONException {
        String url = getBaseUrl() + "user/whoami";

        User submitter = doSamlLogin();

        Request.Builder builder = new Request.Builder().url(url);

        Request request = builder.get().build();

        Response response = client.newCall(request).execute();

        assertEquals(200, response.code());

        JSONObject result = new JSONObject(response.body().string());

        assertEquals(submitter.getId().toString(), result.getString("id"));
        assertEquals("user", result.getString("type"));
        assertTrue(result.getString("uri").endsWith("/data/user/" + submitter.getId()));
    }

    @Test
    public void testHandleRequestWithMalformedUserToken() throws IOException, JSONException {
        HttpUrl url = HttpUrl.parse(getBaseUrl() + "user/whoami").newBuilder()
                .addQueryParameter(Token.USER_TOKEN_PARAM, "MOO").build();

        doSamlLogin();
        Request.Builder builder = new Request.Builder().url(url);

        Request request = builder.get().build();

        Response response = client.newCall(request).execute();

        assertEquals(400, response.code());
    }

    @Test
    public void testHandleRequestWithUserTokenMissingSubmission() throws IOException, JSONException {
        // Create a user token which references a submission that does not exist
        URI mailto = URI.create("mailto:bob@example.com");
        Token token = userTokenFactory.forPassResource("submission", 34, mailto);

        HttpUrl url = HttpUrl.parse(getBaseUrl() + "user/whoami").newBuilder()
                .addQueryParameter(Token.USER_TOKEN_PARAM, token.toString()).build();

        doSamlLogin();

        Request.Builder builder = new Request.Builder().url(url);

        Request request = builder.get().build();

        Response response = client.newCall(request).execute();

        assertEquals(500, response.code());
    }

    @Test
    public void testHandleRequestWithUserToken() throws IOException, JSONException {
        // Create a submission and associated user token

        URI mailto = URI.create("mailto:bob@example.com");
        Submission submission = new Submission();
        submission.setSubmitterName("Bob");
        submission.setSubmitterEmail(mailto);

        try (PassClient client = PassClient.newInstance(refreshableElide)) {
            client.createObject(submission);
        }
        assertEquals(0, submission.getVersion());

        Token token = userTokenFactory.forPassResource("submission", submission.getId(), mailto);

        HttpUrl url = HttpUrl.parse(getBaseUrl() + "user/whoami").newBuilder()
                .addQueryParameter(Token.USER_TOKEN_PARAM, token.toString()).build();

        Request.Builder builder = new Request.Builder().url(url);
        User submitter = doSamlLogin();

        Request request = builder.get().build();

        Response response = client.newCall(request).execute();

        assertEquals(200, response.code());

        JSONObject result = new JSONObject(response.body().string());

        assertEquals(submitter.getId().toString(), result.getString("id"));
        assertEquals("user", result.getString("type"));
        assertTrue(result.getString("uri").endsWith("/data/user/" + submitter.getId()));

        // Submission should be updated

        try (PassClient client = PassClient.newInstance(refreshableElide)) {
            submission = client.getObject(submission.getClass(), submission.getId());

            assertNull(submission.getSubmitterName());
            assertNull(submission.getSubmitterEmail());
            assertEquals(submitter.getId(), submission.getSubmitter().getId());
            assertEquals(1, submission.getVersion());
        }
    }
}
