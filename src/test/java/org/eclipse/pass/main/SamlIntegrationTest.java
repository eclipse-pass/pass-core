package org.eclipse.pass.main;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.yahoo.elide.RefreshableElide;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.eclipse.pass.object.PassClient;
import org.eclipse.pass.object.PassClientSelector;
import org.eclipse.pass.object.RSQL;
import org.eclipse.pass.object.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Run with in memory database and allow a test user to login with SAML.
 * <p>
 * The IDP is provided by {@link TestSamlIdp} which runs in the test JVM. A login is done by
 * posting a signed SAML response to the assertion consumer service of pass-core.
 */
public abstract class SamlIntegrationTest extends SimpleIntegrationTest {
    protected static final String SP_ID = "https://sp.pass/shibboleth";
    protected static final String SP_LOGIN_PATH = "login/saml2/sso/pass";

    protected static OkHttpClient client;

    @Autowired
    protected RefreshableElide refreshableElide;

    @BeforeEach
    protected void setupClient() throws IOException {
        client = newOkhttpClient();
    }

    public String getCsrfToken() throws IOException {
        return getCsrfToken(client);
    }

    /**
     * @return URL of the pass-core assertion consumer service
     */
    protected String getSpLoginUrl() {
        return getBaseUrl() + SP_LOGIN_PATH;
    }

    /**
     * Do a SAML login as the default test user such that the OkHttpClient is authenticated.
     *
     * @return the logged in user
     * @throws IOException
     */
    public User doSamlLogin() throws IOException {
        return doSamlLogin(TestSamlIdp.USER1);
    }

    /**
     * Do a SAML login such that the OkHttpClient is authenticated.
     *
     * @param attributes attributes of the user which the IDP asserts
     * @return the logged in user
     * @throws IOException
     */
    public User doSamlLogin(Map<String, List<String>> attributes) throws IOException {
        String saml_response = TestSamlIdp.createResponse(getSpLoginUrl(), SP_ID, attributes);

        try (Response response = postSamlResponse(saml_response)) {
            assertEquals(200, response.code());
        }

        return get_saml_user(attributes.get(TestSamlIdp.EMAIL).get(0));
    }

    /**
     * Post a SAML response to pass-core as an IDP would by way of the browser.
     *
     * @param saml_response base64 encoded SAML response
     * @return response after following any redirects
     * @throws IOException
     */
    protected Response postSamlResponse(String saml_response) throws IOException {
        FormBody form = new FormBody.Builder().add("SAMLResponse", saml_response).build();

        return client.newCall(new Request.Builder().url(getSpLoginUrl()).post(form).build()).execute();
    }

    private User get_saml_user(String email) throws IOException {
        try (PassClient pass_client = PassClient.newInstance(refreshableElide)) {
            PassClientSelector<User> sel = new PassClientSelector<>(User.class);
            sel.setFilter(RSQL.equals("email", email));

            return pass_client.streamObjects(sel).findFirst().orElseThrow();
        }
    }
}
