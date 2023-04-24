/*
 *
 * Copyright 2023 Johns Hopkins University
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package org.eclipse.pass.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.URI;

import com.yahoo.elide.RefreshableElide;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;
import org.eclipse.pass.main.ShibIntegrationTest;
import org.eclipse.pass.object.PassClient;
import org.eclipse.pass.object.model.AggregatedDepositStatus;
import org.eclipse.pass.object.model.Funder;
import org.eclipse.pass.object.model.Grant;
import org.eclipse.pass.object.model.Policy;
import org.eclipse.pass.object.model.Repository;
import org.eclipse.pass.object.model.Source;
import org.eclipse.pass.object.model.Submission;
import org.eclipse.pass.object.model.SubmissionStatus;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * An integration test for the Policy Service. We populate the data store with objects related to a submission
 * and check that the results for retrieving Policies and Repositories related to the submission are correct.
 *
 * @author jrm
 */
public class PolicyServiceTest extends ShibIntegrationTest {

    @Autowired
    protected RefreshableElide refreshableElide;

    private Submission submission;
    private Repository repository1 = new Repository();
    private Repository repository2 = new Repository();
    private Repository repository3 = new Repository();
    private Policy policy1 = new Policy();
    private Funder funder1 = new Funder();
    private Policy policy2 = new Policy();
    private Policy policy3 = new Policy();
    private Funder funder2 = new Funder();
    private Grant grant = new Grant();

    /**
     * We set up objects - a grant with two funders, each of which has a policy pointing to a
     * repository. The submission has the grant.
     *
     * A third repository unrelated to a funder is the institutional repository.
     * @throws IOException
     */
    @BeforeAll
    public void setupObjects() throws IOException {

        repository1.setName("Repository 1");
        repository1.setDescription("Repository for policy1");

        repository2.setName("Repository 2");
        repository2.setDescription("Repository for policy2");

        repository3.setName("JScholarship");
        repository3.setDescription("JHU Institutional Repository");

        policy1.getRepositories().add(repository1);
        policy1.setInstitution(URI.create("http://www.jhu.edu"));

        funder1.setPolicy(policy1);
        funder1.setName("Direct Funder");

        policy2.getRepositories().add(repository2);

        policy3.setTitle("JHU Open Access Policy");
        policy3.getRepositories().add(repository3);

        funder2.setPolicy(policy2);
        funder2.setName("Primary Funder");

        grant.setProjectName("Test Project");
        grant.setPrimaryFunder(funder2);
        grant.setDirectFunder(funder1);

        submission = new Submission();
        submission.setAggregatedDepositStatus(AggregatedDepositStatus.NOT_STARTED);
        submission.setSubmissionStatus(SubmissionStatus.DRAFT);
        submission.setSubmitterName("Bessie");
        submission.setSource(Source.OTHER);
        submission.setSubmissionStatus(SubmissionStatus.SUBMITTED);
        submission.getGrants().add(grant);

        try (PassClient client = PassClient.newInstance(refreshableElide)) {
            client.createObject(repository1);
            client.createObject(repository2);
            client.createObject(repository3);
            client.createObject(policy1);
            client.createObject(funder1);
            client.createObject(policy2);
            client.createObject(funder2);
            client.createObject(policy3);
            client.createObject(grant);
            client.createObject(submission);
        }
    }

    @Test
    public void SubmissionPoliciesTest() throws IOException, JSONException {
        HttpUrl url = formServiceUrl("policies", submission.getId().toString());
        Request.Builder builder = new Request.Builder();
        setShibHeaders(builder);

        Request okHttpRequest = builder
            .url(url)
            .build();

        Call call = client.newCall(okHttpRequest);

        try (Response okHttpResponse = call.execute()) {
            assertEquals(200, okHttpResponse.code());
            JSONArray result = new JSONArray(okHttpResponse.body().string());
            assertEquals(3, result.length());

            for (int i = 0; i < result.length(); i++) {
                JSONObject obj = result.getJSONObject(i);
                String id =  obj.getString("id");

                if ( id.equals(policy3.getId().toString())) { //the institution's policy
                    assertEquals("institution", obj.getString("type"));
                } else {
                    assertEquals("funder", obj.getString("type"));
                }
            }

        }
    }

    @Test
    public void SubmissionRepositoriesTest() throws IOException, JSONException {
        HttpUrl url = formServiceUrl("repositories", submission.getId().toString());
        Request.Builder builder = new Request.Builder();
        setShibHeaders(builder);

        Request okHttpRequest = builder
            .url(url)
            .build();

        Call call = client.newCall(okHttpRequest);

        try (Response okHttpResponse = call.execute()) {
            assertEquals(200, okHttpResponse.code());
            JSONObject result = new JSONObject(okHttpResponse.body().string());
            assertEquals(2, result.length());

            //the IR - we recommend this by calling it selected
            JSONArray optional = (JSONArray) result.get("optional");
            for (int i = 0; i < optional.length(); i++) {
                JSONObject obj = optional.getJSONObject(i);
                String uri = obj.getString("url");
                assertEquals("true", obj.getString("selected"));
            }

            JSONArray required = (JSONArray) result.get("required");
            for (int i = 0; i < required.length(); i++) {
                JSONObject obj = required.getJSONObject(i);
                String uri = obj.getString("url");
                assertEquals("true", obj.getString("selected"));
            }
        }

    }

    @Test
    public void InvalidSubmissionTest() {
        HttpUrl url = formServiceUrl("repositories", "MOO");
        Request.Builder builder = new Request.Builder();
        setShibHeaders(builder);

        Request okHttpRequest = builder
            .url(url)
            .build();

        Call call = client.newCall(okHttpRequest);
        try {
            call.execute();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private HttpUrl formServiceUrl(String endpoint, String parameterValue) {
        return new HttpUrl.Builder()
            .scheme("http")
            .host("localhost")
            .port(getPort())
            .addPathSegment("policy")
            .addPathSegment(endpoint)
            .addQueryParameter("submission", parameterValue)
            .build();
    }
}
