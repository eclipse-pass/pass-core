package org.eclipse.pass.main;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;

import okhttp3.CookieJar;
import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.JavaNetCookieJar;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public abstract class IntegrationTestBase {
    public static final String BACKEND_USER = "backend";
    public static final String BACKEND_PASSWORD = "moo";
    public static final String BACKEND_CREDENTIALS = Credentials.basic(BACKEND_USER, BACKEND_PASSWORD);

    public final static String JSON_API_CONTENT_TYPE = "application/vnd.api+json";
    public final static MediaType JSON_API_MEDIA_TYPE = MediaType.parse("application/vnd.api+json; charset=utf-8");

    /**
     * @return Base URL for API.
     */
    public abstract String getBaseUrl();

    /**
     * @return OkhttpClient configured to save cookies
     * @throws IOException
     */
    public OkHttpClient newOkhttpClient() throws IOException {
        // Persist cookies for CSRF and SAML
        // Follow redirects for SAML login

        CookieManager cookieManager = new CookieManager();
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        JavaNetCookieJar cookieJar = new JavaNetCookieJar(cookieManager);

        return new OkHttpClient.Builder().cookieJar(cookieJar).build();
    }

    private String get_csrf_token(CookieJar jar) {
        return jar.loadForRequest(HttpUrl.get(getBaseUrl())).stream().
                filter(c -> c.name().equals("XSRF-TOKEN")).findFirst().map(c -> c.value()).orElse(null);
    }

    /**
     * Returns the current CSRF token saved in the cookie jar.
     * If it can't be found, generate a token as the backend user.
     *
     * @return Current CSRF token
     * @throws IOException
     */
    public String getCsrfToken(OkHttpClient client) throws IOException {
        String token = get_csrf_token(client.cookieJar());

        // Make a request to get a token if needed
        if (token == null) {
            String url = getBaseUrl() + "data/grant";
            Request request = new Request.Builder().url(url).header("Accept", JSON_API_CONTENT_TYPE)
                    .addHeader("Content-Type", JSON_API_CONTENT_TYPE)
                    .header("Authorization", BACKEND_CREDENTIALS).get().build();

            Response response = client.newCall(request).execute();

            assertEquals(200, response.code());
        }

        token = get_csrf_token(client.cookieJar());

        assertNotNull(token);

        return token;
    }
}
