package org.eclipse.pass.main;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.IOException;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.yahoo.elide.RefreshableElide;
import io.restassured.RestAssured;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.eclipse.pass.object.PassClient;
import org.eclipse.pass.object.PassClientSelector;
import org.eclipse.pass.object.RSQL;
import org.eclipse.pass.object.model.User;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Run with in memory database and an IDP.
 * A test user can login with SAML.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, classes = Main.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Testcontainers
public class SamlIntegrationTest extends IntegrationTestBase {
    private static final String IDP_IMAGE = "kenchan0130/simplesamlphp:1.19.8";
    private static final String IDP_LOGIN_URL = "http://localhost:8090/simplesaml/module.php/core/loginuserpass.php";
    private static final String IDP_BASE_URL = "http://localhost:8090/";
    private static final String SP_LOGIN_URL = "http://localhost:8080/login/saml2/sso/pass";
    private static final String SP_LOGOUT_URL = "http://localhost:8080/logout";
    private static final String SP_ID = "https://sp.pass/shibboleth";

    private static final String SUBMITTER_USER = "user1";
    private static final String SUBMITTER_PASSWORD = "password";
    private static final String SUBMITTER_EMAIL = "sally123456789@jhu.edu";

    protected static OkHttpClient client;

    // Run a SimpleSAMLphp based IDP at 8090.
    // See https://github.com/kenchan0130/docker-simplesamlphp/ for the Dockerfile.
    // The default asserting party configuration is for this IDP.
    // The metadata for it is in the saml2/idp-metadata.xml and can be retrieved from
    // http://localhost:8090/simplesaml/saml2/idp/metadata.php
    // The volume mount is to setup users with attributes required by PassAuthenticationFilter.
    @Container
    static final GenericContainer<?> IDP_CONTAINER = new GenericContainer<>(DockerImageName.parse(IDP_IMAGE)).
        withCreateContainerCmdModifier(cmd -> {
            cmd.getHostConfig().withPortBindings(new PortBinding(Ports.Binding.bindPort(8090), new ExposedPort(8080)));
        }).withEnv("SIMPLESAMLPHP_SP_ENTITY_ID", SP_ID).
           withEnv("SIMPLESAMLPHP_SP_ASSERTION_CONSUMER_SERVICE", SP_LOGIN_URL).
           withEnv("SIMPLESAMLPHP_IDP_BASE_URL", IDP_BASE_URL).
           withEnv("SIMPLESAMLPHP_SP_SINGLE_LOGOUT_SERVICE", SP_LOGOUT_URL).
           withCopyFileToContainer(MountableFile.forClasspathResource("/saml2/authsources.php"),
                "/var/www/simplesamlphp/config/authsources.php").waitingFor(Wait.forLogMessage(".*apache2.*", 1));

    @LocalServerPort
    private int port;

    @Autowired
    protected RefreshableElide refreshableElide;

    @BeforeAll
    void setup() {
        RestAssured.port = port;
    }

    @BeforeEach
    protected void setupClient() throws IOException {
        client = newOkhttpClient();
    }

    public String getCsrfToken() throws IOException {
        return getCsrfToken(client);
    }

    // Return value of a form input given a marker that should end in value="
    private String get_form_value(String html, String marker) {
        int i = html.indexOf(marker);

        assertNotEquals(i, -1);

        i += marker.length();

        return html.substring(i, html.indexOf('"', i));
    }

    /**
     * Do a SAML login such that the OkHttpClient is authenticated.
     *
     * @return the logged in user
     * @throws IOException
     */
    public User doSamlLogin() throws IOException {
        String url = getBaseUrl() + "data/grant";

        // Ensure that SAML login is initiated by setting Accept to html

        Request request = new Request.Builder().url(url).header("Accept", "text/html, " +
                                                    JSON_API_CONTENT_TYPE).get().build();
        Response response = client.newCall(request).execute();

        assertEquals(200, response.code());

        String html = response.body().string();

        // Fill in the login form and post it, have to grab hidden AuthState input

        String auth_marker = "type=\"hidden\" name=\"AuthState\" value=\"";
        String auth_state = get_form_value(html, auth_marker);

        FormBody form = new FormBody.Builder().add("username", SUBMITTER_USER).
                add("password", SUBMITTER_PASSWORD).add("AuthState", auth_state).build();

        response = client.newCall(new Request.Builder().url(IDP_LOGIN_URL).post(form).build()).execute();

        assertEquals(200, response.code());

        html = response.body().string();

        // Post the saml response that was returned in the form to pass-core

        String saml_marker = "name=\"SAMLResponse\" value=\"";
        String saml_response = get_form_value(html, saml_marker);

        form = new FormBody.Builder().add("SAMLResponse", saml_response).build();

        response = client.newCall(new Request.Builder().url(SP_LOGIN_URL).post(form).build()).execute();

        assertEquals(200, response.code());

        return get_saml_user();
    }

    private User get_saml_user() throws IOException {
        try (PassClient pass_client = PassClient.newInstance(refreshableElide)) {
            PassClientSelector<User> sel = new PassClientSelector<>(User.class);
            sel.setFilter(RSQL.equals("email", SUBMITTER_EMAIL));

            return pass_client.streamObjects(sel).findFirst().orElseThrow();
        }
    }

    @Override
    public String getBaseUrl() {
        return "http://localhost:" + port + "/";
    }

    public int getPort() {
        return port;
    }
}
