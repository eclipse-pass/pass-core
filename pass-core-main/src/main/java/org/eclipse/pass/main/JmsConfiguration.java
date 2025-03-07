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
package org.eclipse.pass.main;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Objects;
import java.util.Set;

import com.amazon.sqs.javamessaging.ProviderConfiguration;
import com.amazon.sqs.javamessaging.SQSConnectionFactory;
import com.yahoo.elide.annotation.LifeCycleHookBinding.Operation;
import com.yahoo.elide.annotation.LifeCycleHookBinding.TransactionPhase;
import com.yahoo.elide.core.dictionary.EntityDictionary;
import com.yahoo.elide.core.dictionary.Injector;
import com.yahoo.elide.core.lifecycle.LifeCycleHook;
import com.yahoo.elide.core.type.Type;
import com.yahoo.elide.core.utils.ClassScanner;
import com.yahoo.elide.core.utils.coerce.CoerceUtil;
import com.yahoo.elide.jsonapi.JsonApiRequestScope;
import com.yahoo.elide.jsonapi.JsonApiSettings;
import com.yahoo.elide.jsonapi.JsonApiSettingsBuilderCustomizer;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.TextMessage;
import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;
import jakarta.persistence.OptimisticLockException;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.pass.main.repository.DepositRepository;
import org.eclipse.pass.main.repository.SubmissionRepository;
import org.eclipse.pass.object.model.Deposit;
import org.eclipse.pass.object.model.EventType;
import org.eclipse.pass.object.model.PassEntity;
import org.eclipse.pass.object.model.Submission;
import org.eclipse.pass.object.model.SubmissionEvent;
import org.eclipse.pass.usertoken.KeyGenerator;
import org.eclipse.pass.usertoken.TokenFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.core.JmsTemplate;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;

/**
 * Configures Elide such that updates to Submission, SubmissionEvent, and Deposit send messages to a JMS broker.
 */
@Configuration
public class JmsConfiguration {
    private static final Logger LOG = LoggerFactory.getLogger(JmsConfiguration.class);

    static final String APPROVAL_LINK_KEY = "approval-link";
    static final String DEPOSIT_MESSAGE_TYPE = "DepositStatus";
    static final String SUBMISSION_MESSAGE_TYPE = "SubmissionReady";
    static final String SUBMISSION_KEY = "submission";
    static final String SUBMISSION_EVENT_MESSAGE_TYPE = "SubmissionEvent";
    static final String DEPOSIT_KEY = "deposit";
    static final String SUBMISSION_EVENT_KEY = "submission-event";
    static final String TYPE_KEY = "type";
    static final String MESSAGE_PROPERTY_TYPE_KEY = "type";

    @Value("${pass.jms.queue.submission}")
    private String submission_queue;

    @Value("${pass.jms.queue.submission-event}")
    private String submission_event_queue;

    @Value("${pass.jms.queue.deposit}")
    private String deposit_queue;

    @Value("${aws.sqs.endpoint-override:AWS_SQS_ENDPOINT_OVERRIDE}")
    private String awsSqsEndpointOverride;

    /**
     * @return name of queue for Submission object updates
     */
    String getSubmissionQueue() {
        return submission_queue;
    }

    /**
     * @return name of queue for SubmissionEvent object updates
     */
    String getSubmissionEventQueue() {
        return submission_event_queue;
    }

    /**
     * @return name of queue for Deposit object updates
     */
    String getDepositQueue() {
        return deposit_queue;
    }

    /**
     * Return TokenFactory configured with a key. Generate the key if it is not given.
     *
     * @param key token key or null
     * @return TokenFactory
     */
    @Bean
    public TokenFactory userTokenFactory(@Value("${pass.usertoken.key:#{null}}") String key) {
        if (key == null || key.isEmpty()) {
            key = KeyGenerator.generateKey();

            LOG.error("No user token key specified, generating key");
        }

        return new TokenFactory(key);
    }

    /**
     * Provide a JMS connection to Amazon SQS if configured to do so.
     *
     * @param awsRegion AWS region
     * @throws URISyntaxException when failing to configure
     * @return JmsListenerContainerFactory
     */
    @Bean
    @ConditionalOnProperty(name = "pass.jms.sqs", havingValue = "true")
    public ConnectionFactory jmsConnectionFactory(@Value("${aws.region}") String awsRegion) throws URISyntaxException {
        SqsClientBuilder sqsClient = configureSqsBuilder(SqsClient.builder(), awsRegion);
        return new SQSConnectionFactory(new ProviderConfiguration(), sqsClient);
    }

    private SqsClientBuilder configureSqsBuilder(SqsClientBuilder sqsClientBuilder, String awsRegion)
        throws URISyntaxException {
        if (StringUtils.isNotEmpty(awsSqsEndpointOverride)) {
            return sqsClientBuilder
                .endpointOverride(new URI(awsSqsEndpointOverride))
                .region(Region.of(awsRegion));
        }
        return sqsClientBuilder.region(Region.of(awsRegion));
    }

    @Bean
    JsonApiSettingsBuilderCustomizer jsonApiSettingsBuilderCustomizer() {
        return JsonApiSettings.JsonApiSettingsBuilderSupport::updateStatus200;
    }

    /**
     * Override the EnitityDictionary bean in order to add hooks.
     *
     * @param injector the Injector
     * @param scanner the ClassScanner
     * @param entitiesToExclude the set of entities to exclude
     * @param jms the JmsTemplate used by the hooks
     * @param userTokenFactory the TokenFactory
     * @param submissionRepository the submission spring data repository
     * @param depositRepository the deposit spring data repository
     * @return configured EntityDictionary.
     */
    @Bean
    public EntityDictionary buildDictionary(Injector injector, ClassScanner scanner,
                                            @Qualifier("entitiesToExclude") Set<Type<?>> entitiesToExclude,
                                            JmsTemplate jms,
                                            TokenFactory userTokenFactory,
                                            SubmissionRepository submissionRepository,
                                            DepositRepository depositRepository) {

        EntityDictionary dictionary = new EntityDictionary(new HashMap<>(), new HashMap<>(), injector,
                CoerceUtil::lookup, entitiesToExclude, scanner, null);

        setupHooks(dictionary, jms, userTokenFactory, submissionRepository, depositRepository);

        return dictionary;
    }

    private void setupHooks(EntityDictionary dictionary, JmsTemplate jms, TokenFactory userTokenFactory,
                            SubmissionRepository submissionRepository, DepositRepository depositRepository) {
        LifeCycleHook<SubmissionEvent> sub_event_hook = (op, phase, event, scope, changes) -> {
            send(jms, submission_event_queue, createMessage(event, userTokenFactory), SUBMISSION_EVENT_MESSAGE_TYPE);
        };

        LifeCycleHook<Submission> sub_hook = (op, phase, sub, scope, changes) -> {
            if (sub.getSubmitted() != null && Boolean.TRUE.equals(sub.getSubmitted())) {
                send(jms, submission_queue, createMessage(sub), SUBMISSION_MESSAGE_TYPE);
            }
        };

        LifeCycleHook<Deposit> deposit_hook = (op, phase, dep, scope, changes) -> {
            send(jms, deposit_queue, createMessage(dep), DEPOSIT_MESSAGE_TYPE);
        };

        dictionary.bindTrigger(SubmissionEvent.class, Operation.CREATE, TransactionPhase.POSTCOMMIT, sub_event_hook,
                false);

        dictionary.bindTrigger(Submission.class, Operation.CREATE, TransactionPhase.POSTCOMMIT, sub_hook, false);
        dictionary.bindTrigger(Submission.class, Operation.UPDATE, TransactionPhase.POSTCOMMIT, sub_hook, false);

        dictionary.bindTrigger(Deposit.class, Operation.CREATE, TransactionPhase.POSTCOMMIT, deposit_hook, false);
        dictionary.bindTrigger(Deposit.class, Operation.UPDATE, TransactionPhase.POSTCOMMIT, deposit_hook, false);

        setupCheckVersionHooks(dictionary, submissionRepository, depositRepository);
    }

    private void setupCheckVersionHooks(EntityDictionary dictionary, SubmissionRepository submissionRepository,
                                        DepositRepository depositRepository) {
        LifeCycleHook<Submission> submission_version_check = (op, phase, sub, scope, changes) -> {
            Long repoSubVersion = submissionRepository.findSubmissionVersionById(sub.getId());
            Long requestVersion = getRequestVersion((JsonApiRequestScope) scope);
            validateEntityVersions(repoSubVersion, requestVersion, sub);
        };

        LifeCycleHook<Deposit> deposit_version_check = (op, phase, dep, scope, changes) -> {
            Long repoDepVersion = depositRepository.findDepositVersionById(dep.getId());
            Long requestVersion = getRequestVersion((JsonApiRequestScope) scope);
            validateEntityVersions(repoDepVersion, requestVersion, dep);
        };

        dictionary.bindTrigger(Submission.class, Operation.UPDATE, TransactionPhase.PREFLUSH,
            submission_version_check, false);
        dictionary.bindTrigger(Deposit.class, Operation.UPDATE, TransactionPhase.PREFLUSH,
            deposit_version_check, false);
    }

    private Long getRequestVersion(JsonApiRequestScope scope) {
        Object requestVersion = scope.getJsonApiDocument().getData().getSingleValue().getAttributes().get("version");
        // The string -> double -> long conversion is needed because json could send number as float
        return Objects.isNull(requestVersion) ? null : Double.valueOf(requestVersion.toString()).longValue();
    }

    private void validateEntityVersions(Long repoVersion, Long requestVersion, PassEntity passEntity) {
        // Need to add 1 here because repoVersion will be the value to be saved after update
        Long checkRequestVersion = Objects.isNull(requestVersion)
            ? Long.valueOf(-1)
            : Long.valueOf(requestVersion + 1);
        if (checkRequestVersion < repoVersion) {
            throw new OptimisticLockException(String.format(
                "Optimistic lock check failed for %s [ID=%d]. Request version: %d, Stored version: %d",
                passEntity.getClass().getSimpleName(), passEntity.getId(), checkRequestVersion, repoVersion));
        }
    }

    private String createMessage(Submission s) {
        return Json.createObjectBuilder().add(SUBMISSION_KEY, s.getId().toString()).add(TYPE_KEY,
                SUBMISSION_MESSAGE_TYPE).build().toString();
    }

    private String getInvitationLink(SubmissionEvent ev, TokenFactory userTokenFactory) {
        Submission sub = ev.getSubmission();

        if (sub != null && sub.getSubmitterEmail() != null && ev.getLink() != null) {
            URI uri = userTokenFactory.forPassResource("submission", sub.getId(), sub.getSubmitterEmail())
                    .addTo(ev.getLink());

            return uri.toString();
        } else {
            LOG.warn("Cannot create an invitation link, missing required information: " + sub);
        }

        return null;
    }

    private String createMessage(SubmissionEvent ev, TokenFactory userTokenFactory) {
        JsonObjectBuilder ob = Json.createObjectBuilder().add(SUBMISSION_EVENT_KEY, ev.getId().toString()).add(TYPE_KEY,
                SUBMISSION_EVENT_MESSAGE_TYPE);

        if (ev.getEventType() == EventType.APPROVAL_REQUESTED_NEWUSER) {
            String link = getInvitationLink(ev, userTokenFactory);

            if (link != null) {
                ob.add(APPROVAL_LINK_KEY, link);
            }
        }

        return ob.build().toString();
    }

    private String createMessage(Deposit dep) {
        return Json.createObjectBuilder().add(DEPOSIT_KEY, dep.getId().toString()).add(TYPE_KEY,
                DEPOSIT_MESSAGE_TYPE).build().toString();
    }

    private void send(JmsTemplate jms, String queue, String text, String type) {
        jms.send(queue, ses -> {
            TextMessage msg = ses.createTextMessage(text);
            msg.setStringProperty(MESSAGE_PROPERTY_TYPE_KEY, type);
            return msg;
        });
    }
}
