/*
 * Copyright 2025 Johns Hopkins University
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
package org.eclipse.pass.doi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

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
import org.eclipse.pass.object.model.PmcParticipation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class DoiServiceTest extends SimpleIntegrationTest {
    private static final String credentials = Credentials.basic(BACKEND_USER, BACKEND_PASSWORD);

    @Autowired
    protected RefreshableElide refreshableElide;

    protected PassClient getNewClient() {
        return PassClient.newInstance(refreshableElide);
    }

    private OkHttpClient httpClient;

    @BeforeEach
    protected void setupClient() throws IOException {
        httpClient = newOkhttpClient();
        try (PassClient passClient = getNewClient()) {
            PassClientSelector<Journal> journalSelector = new PassClientSelector<>(Journal.class);
            List<Journal> testJournals = passClient.streamObjects(journalSelector).toList();
            testJournals.forEach(testJournal -> {
                try {
                    passClient.deleteObject(testJournal);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
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
            assert okHttpResponse.body() != null;
            String body = okHttpResponse.body().string();
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
            assert okHttpResponse.body() != null;
            String body = okHttpResponse.body().string();
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
            assert okHttpResponse.body() != null;
            String body = okHttpResponse.body().string();
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
            assert okHttpResponse.body() != null;
            String body = okHttpResponse.body().string();
            assertEquals("{\"error\":\"Insufficient information to locate or specify a journal entry.\"}",
                         body);
        }
    }

    @Test
    public void testCreateJournal() {
        String name = "Clinical Medicine Insights: Cardiology";
        HttpUrl url = formDoiUrl("10.4137/cmc.s38446" );
        String id = "";

        try (PassClient passClient = getNewClient()) {
            Request okHttpRequest = new Request.Builder()
                .url(url).header("Authorization", credentials)
                .header("X-XSRF-TOKEN", getCsrfToken(httpClient))
                .build();

            Call call = httpClient.newCall(okHttpRequest);
            try (Response okHttpResponse = call.execute()) {
                assertEquals(200, okHttpResponse.code());
                assert okHttpResponse.body() != null;
                String body = okHttpResponse.body().string();
                JsonReader jsonReader1 = Json.createReader(new StringReader(body));
                JsonObject successReport = jsonReader1.readObject();
                jsonReader1.close();
                assertNotNull(successReport.getString("journal-id"));
                id = successReport.getString("journal-id");
            } catch (Exception e) {
                e.printStackTrace();
            }

            //verify that this journal is now in the database
            String filter = RSQL.equals("journalName", name);
            PassClientResult<Journal> result = passClient.
                selectObjects(new PassClientSelector<>(Journal.class, 0, 100, filter, null));
            assertEquals(1, result.getObjects().size());

            //verify that the returned object for the same request has the right id
            call = httpClient.newCall(okHttpRequest);
            try (Response okHttpResponse = call.execute()) {
                assert okHttpResponse.body() != null;
                String body = okHttpResponse.body().string();
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

    @Test
    public void testUpdateJournal() throws Exception {
        final String expectedJournalName = "Publications of the Astronomical Society of the Pacific";
        final HttpUrl doiUrl = formDoiUrl("10.1086/655938" );
        Journal newJournal = new Journal();
        newJournal.setJournalName("TestUpdate: " + expectedJournalName);
        newJournal.setIssns(List.of("Online:1538-3873"));

        executeDoiCalls(newJournal, doiUrl);

        try (PassClient passClient = getNewClient()) {
            //verify that this journal is now in the database
            String filter = RSQL.equals("journalName", expectedJournalName);
            PassClientResult<Journal> result = passClient.
                selectObjects(new PassClientSelector<>(Journal.class, 0, 100, filter, null));
            assertEquals(1, result.getObjects().size());

            Journal updatedJournal = passClient.getObject(Journal.class, newJournal.getId());
            assertEquals(expectedJournalName, updatedJournal.getJournalName());
            assertNull(updatedJournal.getNlmta());
            assertEquals(2, updatedJournal.getIssns().size());
            assertTrue(updatedJournal.getIssns().contains("Online:1538-3873"));
            assertTrue(updatedJournal.getIssns().contains("Print:0004-6280"));
        }
    }

    @Test
    public void testNoUpdateJournalNlmtaNotNull() throws Exception {
        final String expectedJournalName = "Publications of the Astronomical Society of the Pacific";
        final HttpUrl doiUrl = formDoiUrl("10.1086/655938" );
        Journal newJournal = new Journal();
        newJournal.setJournalName("TestUpdate: " + expectedJournalName);
        newJournal.setIssns(List.of("Online:1538-3873"));
        newJournal.setNlmta("Publ Astron Soc Pac");

        executeDoiCalls(newJournal, doiUrl);

        try (PassClient passClient = getNewClient()) {
            String filter = RSQL.equals("journalName", expectedJournalName);
            PassClientResult<Journal> result = passClient.
                selectObjects(new PassClientSelector<>(Journal.class, 0, 100, filter, null));
            assertEquals(0, result.getObjects().size());

            filter = RSQL.equals("journalName", "TestUpdate: " + expectedJournalName);
            result = passClient.
                selectObjects(new PassClientSelector<>(Journal.class, 0, 100, filter, null));
            assertEquals(1, result.getObjects().size());

            Journal actualJournal = passClient.getObject(Journal.class, newJournal.getId());
            assertEquals("TestUpdate: " + expectedJournalName, actualJournal.getJournalName());
            assertEquals("Publ Astron Soc Pac", actualJournal.getNlmta());
            assertEquals(1, actualJournal.getIssns().size());
            assertTrue(actualJournal.getIssns().contains("Online:1538-3873"));
        }
    }

    @Test
    public void testNoUpdateJournalPmcPartNotNull() throws Exception {
        final String expectedJournalName = "Publications of the Astronomical Society of the Pacific";
        final HttpUrl doiUrl = formDoiUrl("10.1086/655938" );
        Journal newJournal = new Journal();
        newJournal.setJournalName("TestUpdate: " + expectedJournalName);
        newJournal.setIssns(List.of("Online:1538-3873"));
        newJournal.setPmcParticipation(PmcParticipation.A);

        executeDoiCalls(newJournal, doiUrl);

        try (PassClient passClient = getNewClient()) {
            String filter = RSQL.equals("journalName", expectedJournalName);
            PassClientResult<Journal> result = passClient.
                selectObjects(new PassClientSelector<>(Journal.class, 0, 100, filter, null));
            assertEquals(0, result.getObjects().size());

            filter = RSQL.equals("journalName", "TestUpdate: " + expectedJournalName);
            result = passClient.
                selectObjects(new PassClientSelector<>(Journal.class, 0, 100, filter, null));
            assertEquals(1, result.getObjects().size());

            Journal actualJournal = passClient.getObject(Journal.class, newJournal.getId());
            assertEquals("TestUpdate: " + expectedJournalName, actualJournal.getJournalName());
            assertNull(actualJournal.getNlmta());
            assertEquals(1, actualJournal.getIssns().size());
            assertTrue(actualJournal.getIssns().contains("Online:1538-3873"));
        }
    }

    private void executeDoiCalls(Journal newJournal, HttpUrl doiUrl) throws Exception {
        try (PassClient passClient = getNewClient()) {
            passClient.createObject(newJournal);

            Request okHttpRequest = new Request.Builder()
                .url(doiUrl).header("Authorization", credentials)
                .header("X-XSRF-TOKEN", getCsrfToken(httpClient))
                .build();

            String id;
            Call call = httpClient.newCall(okHttpRequest);
            try (Response okHttpResponse = call.execute()) {
                assertEquals(200, okHttpResponse.code());
                assert okHttpResponse.body() != null;
                String body = okHttpResponse.body().string();
                JsonReader jsonReader1 = Json.createReader(new StringReader(body));
                JsonObject successReport = jsonReader1.readObject();
                jsonReader1.close();
                assertNotNull(successReport.getString("journal-id"));
                id = successReport.getString("journal-id");
                assertEquals(newJournal.getId(), Long.parseLong(id));
            }
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
