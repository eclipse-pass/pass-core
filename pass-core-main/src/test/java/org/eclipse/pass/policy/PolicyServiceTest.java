package org.eclipse.pass.policy;

import java.io.IOException;
import java.net.URI;

import com.yahoo.elide.RefreshableElide;
import okhttp3.Call;
import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.eclipse.pass.main.IntegrationTest;
import org.eclipse.pass.object.ElideDataStorePassClient;
import org.eclipse.pass.object.PassClient;
import org.eclipse.pass.object.model.AggregatedDepositStatus;
import org.eclipse.pass.object.model.Funder;
import org.eclipse.pass.object.model.Grant;
import org.eclipse.pass.object.model.Policy;
import org.eclipse.pass.object.model.Repository;
import org.eclipse.pass.object.model.Source;
import org.eclipse.pass.object.model.Submission;
import org.eclipse.pass.object.model.SubmissionStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class PolicyServiceTest extends IntegrationTest {

    @Autowired
    protected RefreshableElide refreshableElide;

    protected PassClient getNewClient() {
        return new ElideDataStorePassClient(refreshableElide);
    }

    private OkHttpClient httpClient = new OkHttpClient();
    private String credentials = Credentials.basic(BACKEND_USER, BACKEND_PASSWORD);

    @Test
    public void SubmissionPolicyTest() throws IOException {

        Repository repository1 = new Repository();
        repository1.setName("Repository 1");
        repository1.setDescription("Repository for policy1");

        Repository repository2 = new Repository();
        repository1.setName("Repository 2");
        repository1.setDescription("Repository for policy2");

        Policy policy1 = new Policy();
        policy1.getRepositories().add(repository1);
        policy1.setInstitution(URI.create("http://www.jhu.edu"));

        Funder funder1 = new Funder();
        funder1.setPolicy(policy1);
        funder1.setName("Direct Funder");

        Policy policy2 = new Policy();
        policy2.getRepositories().add(repository2);

        Funder funder2 = new Funder();
        funder2.setPolicy(policy2);
        funder2.setName("Primary Funder");

        Grant grant = new Grant();
        grant.setProjectName("Test Project");
        grant.setPrimaryFunder(funder2);
        grant.setDirectFunder(funder1);

        Submission submission = new Submission();
        submission.setAggregatedDepositStatus(AggregatedDepositStatus.NOT_STARTED);
        submission.setSubmissionStatus(SubmissionStatus.DRAFT);
        submission.setSubmitterName("Bessie");
        submission.setSource(Source.OTHER);
        submission.setSubmissionStatus(SubmissionStatus.SUBMITTED);
        submission.getGrants().add(grant);

        try (PassClient client = getNewClient()) {
            client.createObject(repository1);
            client.createObject(repository2);
            client.createObject(policy1);
            client.createObject(funder1);
            client.createObject(policy2);
            client.createObject(funder2);
            client.createObject(grant);
            client.createObject(submission);
        }

        HttpUrl url = formServiceUrl("policies", submission.getId().toString());
        Request okHttpRequest = new Request.Builder()
            .url(url).header("Authorization", credentials)
            .build();

        System.out.println(okHttpRequest.url().toString());
        System.out.println(okHttpRequest.headers().toString());
        Call call = httpClient.newCall(okHttpRequest);

        try (Response okHttpResponse = call.execute()) {
            System.out.println(okHttpResponse.body().contentLength());
//            assertEquals(200, okHttpResponse.code());
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
