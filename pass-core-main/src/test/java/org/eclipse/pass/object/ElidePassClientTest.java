/*
 * Copyright 2022 Johns Hopkins University
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
package org.eclipse.pass.object;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.yahoo.elide.RefreshableElide;
import org.eclipse.pass.main.SimpleIntegrationTest;
import org.eclipse.pass.object.model.AggregatedDepositStatus;
import org.eclipse.pass.object.model.Deposit;
import org.eclipse.pass.object.model.DepositStatus;
import org.eclipse.pass.object.model.Funder;
import org.eclipse.pass.object.model.Grant;
import org.eclipse.pass.object.model.Journal;
import org.eclipse.pass.object.model.PmcParticipation;
import org.eclipse.pass.object.model.Publication;
import org.eclipse.pass.object.model.RepositoryCopy;
import org.eclipse.pass.object.model.Source;
import org.eclipse.pass.object.model.Submission;
import org.eclipse.pass.object.model.SubmissionEvent;
import org.eclipse.pass.object.model.SubmissionStatus;
import org.eclipse.pass.object.model.User;
import org.eclipse.pass.object.model.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Tests must be written such that they can run in any order and handle objects already existing.
 */
public class ElidePassClientTest extends SimpleIntegrationTest {
    protected PassClient client;

    @Autowired
    protected RefreshableElide refreshableElide;

    protected PassClient getNewClient() {
        return new ElidePassClient(refreshableElide);
    }

    @BeforeEach
    public void setupClient() {
        client = getNewClient();
    }

    @AfterEach
    public void cleanupClient() throws IOException {
        client.close();
    }

    public void refreshClient() throws IOException {
        client.close();
        client = getNewClient();
    }

    @Test
    public void testCreateObject() throws IOException {
        Journal journal = new Journal();

        assertNull(journal.getId());

        journal.setJournalName("Test journal");
        journal.setNlmta("hmm");
        journal.setPmcParticipation(PmcParticipation.A);
        journal.setIssns(List.of("test1", "test2"));
        client.createObject(journal);

        assertNotNull(journal.getId());

        Journal test = client.getObject(Journal.class, journal.getId());

        assertEquals(journal, test);
    }

    @Test
    public void testGetObjectNotExisting() throws IOException {
        Journal test = client.getObject(Journal.class, 10000000L);

        assertNull(test);
    }

    @Test
    public void testUpdateObject() throws IOException {
        Submission submission = new Submission();

        submission.setAggregatedDepositStatus(AggregatedDepositStatus.NOT_STARTED);
        submission.setSubmissionStatus(SubmissionStatus.DRAFT);
        submission.setSubmitterName("Bessie");

        client.createObject(submission);

        submission.setSource(Source.OTHER);
        submission.setSubmissionStatus(SubmissionStatus.SUBMITTED);

        client.updateObject(submission);

        Submission test = client.getObject(submission.getClass(), submission.getId());

        assertEquals(submission.getId(), test.getId());
        assertEquals(submission.getAggregatedDepositStatus(), test.getAggregatedDepositStatus());
        assertEquals(submission.getSubmitterName(), test.getSubmitterName());
        assertEquals(submission.getSource(), test.getSource());
        assertEquals(submission.getSubmissionStatus(), test.getSubmissionStatus());
        assertEquals(submission.getMetadata(), test.getMetadata());

        // The lazy loading of objects from relationships does not play nicely with equality tests
        // assertEquals(submission, test);
    }

    @Test
    public void testMultipleUpdatesToSameObject() throws IOException {
        Funder funder = new Funder();
        funder.setName("This is a name");

        client.createObject(funder);

        Funder test = client.getObject(Funder.class, funder.getId());

        assertEquals(funder.getName(), test.getName());
        assertEquals(funder.getUrl(), test.getUrl());
        assertEquals(funder.getLocalKey(), test.getLocalKey());

        funder.setUrl(URI.create("http://example.com"));
        client.updateObject(funder);

        // The first getObject seems to be cached
        refreshClient();
        test = client.getObject(Funder.class, funder.getId());

        assertEquals(funder.getName(), test.getName());
        assertEquals(funder.getUrl(), test.getUrl());
        assertEquals(funder.getLocalKey(), test.getLocalKey());

        funder.setLocalKey("key");
        client.updateObject(funder);

        refreshClient();
        test = client.getObject(Funder.class, funder.getId());

        assertEquals(funder.getName(), test.getName());
        assertEquals(funder.getUrl(), test.getUrl());
        assertEquals(funder.getLocalKey(), test.getLocalKey());
    }

    @Test
    public void testDeleteObject() throws IOException {
        SubmissionEvent ev = new SubmissionEvent();
        ev.setComment("This is a comment");

        client.createObject(ev);

        SubmissionEvent test = client.getObject(ev.getClass(), ev.getId());

        assertEquals(ev.getComment(), test.getComment());

        client.deleteObject(ev);

        test = client.getObject(ev.getClass(), ev.getId());

        assertNull(test);
    }

    @Test
    public void testSelectObjects() throws IOException {
        int num_grants = 10;

        // All grants created in this test will share the same localKey
        String key = "key: " + UUID.randomUUID();
        for (int i = 0; i < num_grants; i++) {
            User pi = new User();

            pi.setDisplayName("user " + i);
            pi.setEmail("pi" + i + "@example.com");

            client.createObject(pi);

            User copi1 = new User();
            copi1.setDisplayName("copi 1 of user " + i);
            copi1.setLastName("Boberson");

            client.createObject(copi1);

            User copi2 = new User();
            copi2.setDisplayName("copi 2 of user " + i);
            copi2.setLastName("Bobert");

            client.createObject(copi2);

            Grant grant = new Grant();
            grant.setAwardDate(ZonedDateTime.now());
            grant.setAwardNumber("award:" + i);
            grant.setLocalKey(key);
            grant.setPi(pi);
            grant.getCoPis().add(copi1);
            grant.getCoPis().add(copi2);

            client.createObject(grant);
        }

        String filter = RSQL.equals("localKey", key);
        PassClientResult<Grant> result = client.selectObjects(new PassClientSelector<>(Grant.class, 0,
                100, filter, null));

        assertEquals(num_grants, result.getObjects().size());
        assertEquals(num_grants, result.getTotal());

        result.getObjects().forEach(g -> {
            assertTrue(g.getPi().getDisplayName().startsWith("user"));
            assertEquals(2, g.getCoPis().size());
        });

        result = client.selectObjects(new PassClientSelector<>(Grant.class, 0, 5, filter, null));

        assertEquals(5, result.getObjects().size());
        assertEquals(num_grants, result.getTotal());

        filter = RSQL.and(RSQL.equals("localKey", key), RSQL.equals("awardNumber", "award:3"));
        result = client.selectObjects(new PassClientSelector<>(Grant.class, 0, 100, filter, "id"));

        assertEquals(1, result.getObjects().size());
        assertEquals(1, result.getTotal());
        assertEquals(result.getObjects().get(0).getAwardNumber(), "award:3");
    }

    @Test
    public void testStreamObjects() throws IOException {
        List<Journal> journals = new ArrayList<>();
        int num_pubs = 10;
        int num_journals = 4;

        for (int i = 0; i < num_journals; i++) {
            Journal journal = new Journal();

            journal.setJournalName("Journal of Bovine Studies: " + i);
            journal.setNlmta("nmlta " + i);

            client.createObject(journal);

            journals.add(journal);
        }

        String pmid = "pmid:" + UUID.randomUUID();
        for (int i = 0; i < num_pubs; i++) {
            Publication pub = new Publication();

            pub.setTitle("Title with Large Words: " + i);
            pub.setJournal(journals.get(i % num_journals));
            pub.setPmid(pmid);

            client.createObject(pub);
        }

        String filter = RSQL.equals("pmid", pmid);
        List<Publication> pubs = client.streamObjects(new PassClientSelector<>(Publication.class,
                0, 2, filter, null)).collect(Collectors.toList());

        assertEquals(num_pubs, pubs.size());

        pubs.forEach(p -> {
            assertNotNull(p);
            assertNotNull(p.getId());
            assertEquals(pmid, p.getPmid());
            assertTrue(p.getTitle().startsWith("Title"));
            assertNotNull(p.getJournal());
            assertTrue(p.getJournal().getJournalName().startsWith("Journal"));
        });
    }

    @Test
    public void testHasMember() throws IOException {
        User user = new User();

        user.setFirstName("Bob");
        user.setDisplayName("Bobby B. Good");
        user.setLastName("Boboson");

        String locatorid1 = UUID.randomUUID().toString();
        String locatorid2 = UUID.randomUUID().toString();

        user.setLocatorIds(List.of(locatorid1, locatorid2));

        client.createObject(user);

        PassClientSelector<User> selector = new PassClientSelector<>(User.class);
        selector.setFilter(RSQL.hasMember("locatorIds", locatorid1));

        List<User> result = client.streamObjects(selector).collect(Collectors.toList());

        assertEquals(1, result.size());
        assertEquals(user, result.get(0));

        refreshClient();
        selector = new PassClientSelector<>(User.class);
        selector.setFilter(RSQL.hasMember("locatorIds", "badvalue"));
        result = client.streamObjects(selector).collect(Collectors.toList());

        assertEquals(0, result.size());

        refreshClient();

        selector = new PassClientSelector<>(User.class);
        selector.setFilter(RSQL.hasMember("locatorIds", locatorid2));
        result = client.streamObjects(selector).collect(Collectors.toList());
        assertEquals(1, result.size());
        assertEquals(user, result.get(0));
    }

    @Test
    public void testHasRepositoryCopy() throws IOException {
        RepositoryCopy rc = new RepositoryCopy();

        rc.setAccessUrl(URI.create("http://example.com"));

        String externalid1 = UUID.randomUUID().toString();
        String externalid2 = UUID.randomUUID().toString();

        rc.setExternalIds(List.of(externalid1, externalid2));

        client.createObject(rc);

        PassClientSelector<RepositoryCopy> selector = new PassClientSelector<>(RepositoryCopy.class);
        selector.setFilter(RSQL.hasMember("externalIds", externalid1));

        List<RepositoryCopy> result = client.streamObjects(selector).collect(Collectors.toList());
        assertEquals(1, result.size());
        assertEquals(rc, result.get(0));
    }

    @Test
    public void testListMemberEquality() throws IOException {
        User user = new User();
        user.setDisplayName("Listy Lister");

        user.setRoles(List.of(UserRole.ADMIN, UserRole.SUBMITTER));
        user.setLocatorIds(List.of("test1", "test2"));

        client.createObject(user);

        User test_user = client.getObject(user.getClass(), user.getId());

        // Hibernate list implementation triggered by ElementCollection does not support equals
        // assertEquals(user.getLocatorIds(), test_user.getLocatorIds());

        assertEquals(user.getRoles(), test_user.getRoles());
        assertEquals(user, test_user);

        Journal journal = new Journal();
        journal.setJournalName("Hibernate list testing");
        journal.setIssns(List.of("a", "b", "c", "d"));

        client.createObject(journal);

        Journal test_journal = client.getObject(Journal.class, journal.getId());

        // Hibernate list implementation triggered by ElementCollection does not support equals
        // assertEquals(journal.getIssns(), test_journal.getIssns());

        assertEquals(journal, test_journal);
    }

    @Test
    public void testCreateDeposit() throws IOException {
        Deposit deposit = new Deposit();
        deposit.setDepositStatus(DepositStatus.REJECTED);
        deposit.setStatusMessage("test status message");
        deposit.setDepositStatusRef("test status ref");

        client.createObject(deposit);

        Deposit actualDeposit = client.getObject(deposit.getClass(), deposit.getId());
        assertNotNull(deposit.getId());
        assertEquals(DepositStatus.REJECTED, actualDeposit.getDepositStatus());
        assertEquals("test status message", actualDeposit.getStatusMessage());
        assertEquals("test status ref", actualDeposit.getDepositStatusRef());
    }

    @Test
    public void testUpdateObject_CheckVersionUpdate() throws IOException {
        // GIVEN
        Submission submission = new Submission();
        submission.setAggregatedDepositStatus(AggregatedDepositStatus.NOT_STARTED);
        submission.setSubmissionStatus(SubmissionStatus.DRAFT);
        submission.setSubmitterName("Bessie");

        // WHEN
        client.createObject(submission);

        // THEN
        assertEquals(0, submission.getVersion());

        // WHEN
        submission.setSource(Source.OTHER);
        submission.setSubmissionStatus(SubmissionStatus.SUBMITTED);
        client.updateObject(submission);

        // THEN
        assertEquals(1, submission.getVersion());

        Submission test = client.getObject(submission.getClass(), submission.getId());

        assertEquals(submission.getId(), test.getId());
        assertEquals(submission.getAggregatedDepositStatus(), test.getAggregatedDepositStatus());
        assertEquals(submission.getSubmitterName(), test.getSubmitterName());
        assertEquals(submission.getSource(), test.getSource());
        assertEquals(submission.getSubmissionStatus(), test.getSubmissionStatus());
        assertEquals(submission.getMetadata(), test.getMetadata());
        assertEquals(1, test.getVersion());

        // The lazy loading of objects from relationships does not play nicely with equality tests
        // assertEquals(submission, test);
    }

    @Test
    public void testUpdateSubmission_OptimisticLocking() throws IOException {
        // GIVEN
        Submission submission = new Submission();
        submission.setAggregatedDepositStatus(AggregatedDepositStatus.NOT_STARTED);
        submission.setSubmissionStatus(SubmissionStatus.DRAFT);
        submission.setSubmitterName("Bessie");
        client.createObject(submission);

        Submission updateSub1 = client.getObject(submission.getClass(), submission.getId());
        // This is needed to simulate second client get because client.getObject returns same instance of Submission
        // in this test.
        Submission updateSub2 = new Submission(updateSub1);

        updateSub1.setSource(Source.OTHER);
        updateSub1.setSubmissionStatus(SubmissionStatus.SUBMITTED);
        client.updateObject(updateSub1);

        // WHEN/THEN
        IOException ioException = assertThrows(IOException.class, () -> {
            updateSub2.setSource(null);
            updateSub2.setSubmissionStatus(SubmissionStatus.CHANGES_REQUESTED);
            client.updateObject(updateSub2);
        });

        assertTrue(ioException.getMessage().startsWith("Failed to update object: 409 " +
            "{\"errors\":[{\"detail\":\"Optimistic lock check failed for Submission [ID="));
        assertTrue(ioException.getMessage().endsWith("]. Request version: 1, Stored version: 2\"}]}"));
    }

    @Test
    public void testUpdateSubmission_OptimisticLocking_NullVersion() throws IOException {
        // GIVEN
        Submission submission = new Submission();
        submission.setAggregatedDepositStatus(AggregatedDepositStatus.NOT_STARTED);
        submission.setSubmissionStatus(SubmissionStatus.DRAFT);
        submission.setSubmitterName("Bessie");
        client.createObject(submission);

        Submission updateSub1 = client.getObject(submission.getClass(), submission.getId());
        Submission updateSub2 = client.getObject(submission.getClass(), submission.getId());

        updateSub1.setSource(Source.OTHER);
        updateSub1.setSubmissionStatus(SubmissionStatus.SUBMITTED);

        client.updateObject(updateSub1);

        // WHEN/THEN
        IOException ioException = assertThrows(IOException.class, () -> {
            updateSub2.setSource(null);
            updateSub2.setSubmissionStatus(SubmissionStatus.CHANGES_REQUESTED);
            ReflectionTestUtils.setField(updateSub2, "version", null);
            client.updateObject(updateSub2);
        });

        assertTrue(ioException.getMessage().startsWith("Failed to update object: 409 " +
            "{\"errors\":[{\"detail\":\"Optimistic lock check failed for Submission [ID="));
        assertTrue(ioException.getMessage().endsWith("]. Request version: -1, Stored version: 2\"}]}"));
    }

    @Test
    public void testUpdateDeposit_OptimisticLocking() throws IOException {
        // GIVEN
        Deposit deposit = new Deposit();
        deposit.setDepositStatus(DepositStatus.SUBMITTED);
        client.createObject(deposit);

        Deposit updateDep1 = client.getObject(deposit.getClass(), deposit.getId());
        // This is needed to simulate second client get because client.getObject returns same instance of Deposit
        // in this test.
        Deposit updateDep2 = new Deposit(updateDep1);

        updateDep1.setDepositStatus(DepositStatus.FAILED);
        client.updateObject(updateDep1);

        // WHEN/THEN
        IOException ioException = assertThrows(IOException.class, () -> {
            updateDep2.setDepositStatus(DepositStatus.ACCEPTED);
            client.updateObject(updateDep2);
        });

        assertTrue(ioException.getMessage().startsWith("Failed to update object: 409 " +
            "{\"errors\":[{\"detail\":\"Optimistic lock check failed for Deposit [ID="));
        assertTrue(ioException.getMessage().endsWith("]. Request version: 1, Stored version: 2\"}]}"));
    }

    @Test
    public void testUpdateDeposit_OptimisticLocking_NullVersion() throws IOException {
        // GIVEN
        Deposit deposit = new Deposit();
        deposit.setDepositStatus(DepositStatus.SUBMITTED);
        client.createObject(deposit);

        Deposit updateDep1 = client.getObject(deposit.getClass(), deposit.getId());
        Deposit updateDep2 = client.getObject(deposit.getClass(), deposit.getId());

        updateDep1.setDepositStatus(DepositStatus.FAILED);
        client.updateObject(updateDep1);

        // WHEN/THEN
        IOException ioException = assertThrows(IOException.class, () -> {
            updateDep2.setDepositStatus(DepositStatus.ACCEPTED);
            ReflectionTestUtils.setField(updateDep2, "version", null);
            client.updateObject(updateDep2);
        });

        assertTrue(ioException.getMessage().startsWith("Failed to update object: 409 " +
            "{\"errors\":[{\"detail\":\"Optimistic lock check failed for Deposit [ID="));
        assertTrue(ioException.getMessage().endsWith("]. Request version: -1, Stored version: 2\"}]}"));
    }

    @Test
    public void testUpdateDeposit_OptimisticLocking_NoUpdate() throws IOException {
        // GIVEN
        Deposit deposit = new Deposit();
        deposit.setDepositStatus(DepositStatus.SUBMITTED);
        client.createObject(deposit);

        Deposit updateDep1 = client.getObject(deposit.getClass(), deposit.getId());
        updateDep1.setDepositStatus(DepositStatus.FAILED);
        client.updateObject(updateDep1);

        // WHEN
        client.updateObject(updateDep1);

        // THEN
        assertEquals(DepositStatus.FAILED, updateDep1.getDepositStatus());
        assertEquals(1, updateDep1.getVersion());
    }
}
