package org.eclipse.pass.doi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.StringReader;

import com.yahoo.elide.RefreshableElide;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import okhttp3.Call;
import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.eclipse.pass.main.SimpleIntegrationTest;
import org.eclipse.pass.object.PassClient;
import org.eclipse.pass.object.PassClientResult;
import org.eclipse.pass.object.PassClientSelector;
import org.eclipse.pass.object.RSQL;
import org.eclipse.pass.object.model.Journal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class DoiServiceTest extends SimpleIntegrationTest {
    private static String credentials = Credentials.basic(BACKEND_USER, BACKEND_PASSWORD);

    @Autowired
    protected RefreshableElide refreshableElide;

    protected PassClient getNewClient() {
        return PassClient.newInstance(refreshableElide);
    }

    private OkHttpClient httpClient;

    @BeforeEach
    protected void setupClient() throws IOException {
        httpClient = newOkhttpClient();
    }

    /**
     * throw in a "moo" doi, expect a 400 error
     *
     * @throws Exception if something goes wrong
     */
    @Test
    public void invalidDoiTest() throws Exception {
        HttpUrl url = formDoiUrl("moo");

        Request okHttpRequest = new Request.Builder()
            .url(url).header("Authorization", credentials)
            .header("X-XSRF-TOKEN", getCsrfToken(httpClient))
            .build();
        Call call = httpClient.newCall(okHttpRequest);
        try (Response okHttpResponse = call.execute()) {
            assertEquals(400, okHttpResponse.code());
            String body = okHttpResponse.body().string();
            assert body != null;
            assertEquals("{\"error\":\"Supplied DOI is not in valid DOI format.\"}",
                         body);

        }
    }

    /**
     * throw in a null doi, expect a 400 error
     *
     * @throws Exception if something goes wrong
     */
    @Test
    public void nullDoiTest() throws Exception {
        HttpUrl url = formDoiUrl(null);

        Request okHttpRequest = new Request.Builder()
            .url(url).header("Authorization", credentials)
            .header("X-XSRF-TOKEN", getCsrfToken(httpClient))
            .build();
        Call call = httpClient.newCall(okHttpRequest);
        try (Response okHttpResponse = call.execute()) {
            assertEquals(400, okHttpResponse.code());
            String body = okHttpResponse.body().string();
            assert body != null;
            assertEquals("{\"error\":\"Supplied DOI is not in valid DOI format.\"}",
                         body);

        }
    }

    /**
     * throw in a valid but nonsense doi, expect a 404 error
     *
     * @throws Exception if something goes wrong
     */
    @Test
    public void noSuchDoiTest() throws Exception {

        HttpUrl url = formDoiUrl( "10.1212/abc.DEF");

        Request okHttpRequest = new Request.Builder()
            .url(url).header("Authorization", credentials)
            .header("X-XSRF-TOKEN", getCsrfToken(httpClient))
            .build();
        Call call = httpClient.newCall(okHttpRequest);
        try (Response okHttpResponse = call.execute()) {
            assertEquals(404, okHttpResponse.code());
            String body = okHttpResponse.body().string();
            assert body != null;
            assertEquals("{\"error\":\"The resource for DOI 10.1212/abc.DEF could not be found on Crossref.\"}",
                         body);

        }
    }

    /**
     * test that a valid dois for a book gives the appropriate error - since it has no issns, it does not have
     * sufficient
     * info to specify a journal
     *
     * @throws Exception if something goes wrong
     */
    @Test
    public void bookDoiFailTest() throws Exception {
        // books have isbn, not issn - this should cause a failure

        HttpUrl url = formDoiUrl("10.1002/0470841559.ch1");

        Request okHttpRequest = new Request.Builder()
            .url(url).header("Authorization", credentials)
            .header("X-XSRF-TOKEN", getCsrfToken(httpClient))
            .build();
        Call call = httpClient.newCall(okHttpRequest);
        try (Response okHttpResponse = call.execute()) {
            assertEquals(422, okHttpResponse.code());
            String body = okHttpResponse.body().string();
            assert body != null;
            assertEquals("{\"error\":\"Insufficient information to locate or specify a journal entry.\"}",
                         body);
        }
    }

    @Test
    public void realJournalTest() {
        String name = "Clinical Medicine Insights: Cardiology";
        HttpUrl url = formDoiUrl("10.4137/cmc.s38446" );
        String id = "";

        try (PassClient passClient = getNewClient()) {
            //if this journal is in the database already, delete it
            String filter = RSQL.equals("journalName", name);
            PassClientResult<Journal> result = passClient.
                selectObjects(new PassClientSelector<>(Journal.class, 0, 100, filter, null));
            result.getObjects().forEach(j -> {
                try {
                    passClient.deleteObject(Journal.class, j.getId());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

            //verify that this journal is not in the database
            filter = RSQL.equals("journalName", name);
            result = passClient.
                selectObjects(new PassClientSelector<>(Journal.class, 0, 100, filter, null));
            assertEquals(0, result.getObjects().size());

            Request okHttpRequest = new Request.Builder()
                .url(url).header("Authorization", credentials)
                .header("X-XSRF-TOKEN", getCsrfToken(httpClient))
                .build();

            Call call = httpClient.newCall(okHttpRequest);
            try (Response okHttpResponse = call.execute()) {
                assertEquals(200, okHttpResponse.code());
                String body = okHttpResponse.body().string();
                assert body != null;
                JsonReader jsonReader1 = Json.createReader(new StringReader(body));
                JsonObject successReport = jsonReader1.readObject();
                jsonReader1.close();
                assertNotNull(successReport.getString("journal-id"));
                id = successReport.getString("journal-id");

                //verify that this journal is now in the database
                filter = RSQL.equals("journalName", name);
                result = passClient.
                    selectObjects(new PassClientSelector<>(Journal.class, 0, 100, filter, null));
                assertEquals(1, result.getObjects().size());
            } catch (Exception e) {
                e.printStackTrace();
            }

            //verify that the returned object for the same request has the right id
            call = httpClient.newCall(okHttpRequest);
            try (Response okHttpResponse = call.execute()) {
                String body = okHttpResponse.body().string();
                assert body != null;
                JsonReader jsonReader2 = Json.createReader(new StringReader(body));
                JsonObject successReport = jsonReader2.readObject();
                jsonReader2.close();
                assertEquals(id, successReport.getString("journal-id"));
            } catch (Exception e) {
                e.printStackTrace();
            }

            //and that there is only one of them in the database
            filter = RSQL.equals("journalName", name);
            result = passClient.
                selectObjects(new PassClientSelector<>(Journal.class, 0, 100, filter, null));
            assertEquals(1, result.getObjects().size());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private HttpUrl formDoiUrl(String doi) {
        return new HttpUrl.Builder()
            .scheme("http")
            .host("localhost")
            .port(getPort())
            .addPathSegment("doi")
            .addPathSegment("journal")
            .addQueryParameter("doi", doi)
            .build();
    }

}
