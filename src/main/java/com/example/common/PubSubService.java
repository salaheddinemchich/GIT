package com.example.common;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpStatusCodes;
import com.google.api.services.pubsub.Pubsub;
import com.google.api.services.pubsub.model.AcknowledgeRequest;
import com.google.api.services.pubsub.model.PublishRequest;
import com.google.api.services.pubsub.model.PubsubMessage;
import com.google.api.services.pubsub.model.PullRequest;
import com.google.api.services.pubsub.model.PullResponse;
import com.google.api.services.pubsub.model.ReceivedMessage;
import com.google.api.services.pubsub.model.Subscription;
import com.google.api.services.pubsub.model.Topic;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Thin wrapper over the legacy Google Cloud Pub/Sub REST v1 client
 * ({@code com.google.api.services.pubsub}) — publish, consume (pull + ack in
 * one call), and topic/subscription provisioning. Pure HTTP/JSON; no gRPC.
 *
 * <p>This is the single Pub/Sub seam the Kafka migration replaces: every
 * {@code projects().topics()...} / {@code projects().subscriptions()...} call
 * lives here, so a migration only has to rewrite this class plus
 * {@link PubsubClientProducer} and {@link PubSubConfig}.
 */
@ApplicationScoped
public class PubSubService {

    private static final Logger log = LoggerFactory.getLogger(PubSubService.class);

    @Inject
    Pubsub pubsub;

    /** Publish a single text message (pipe-delimited payload) to a topic. */
    public void publish(String fullTopic, String payload) {
        PubsubMessage message = new PubsubMessage();
        message.encodeData(payload.getBytes(StandardCharsets.UTF_8));
        PublishRequest request = new PublishRequest().setMessages(List.of(message));
        try {
            pubsub.projects().topics().publish(fullTopic, request).execute();
        } catch (IOException e) {
            throw new IllegalStateException("Publish to " + fullTopic + " failed: " + e.getMessage(), e);
        }
    }

    /**
     * Pull up to {@code maxMessages}, hand each decoded payload to
     * {@code handler}, then acknowledge the ones that processed cleanly. A
     * pull failure (no emulator / no credentials) is logged and swallowed so
     * the scheduled poller keeps retrying instead of crashing.
     */
    public void consume(String fullSubscription, int maxMessages, Consumer<String> handler) {
        List<ReceivedMessage> messages;
        try {
            PullRequest request = new PullRequest().setMaxMessages(maxMessages).setReturnImmediately(true);
            PullResponse response = pubsub.projects().subscriptions().pull(fullSubscription, request).execute();
            messages = response.getReceivedMessages();
        } catch (IOException e) {
            log.warn("Pull from {} failed: {}", fullSubscription, e.getMessage());
            return;
        }
        if (messages == null || messages.isEmpty()) return;

        List<String> ackIds = new ArrayList<>();
        for (ReceivedMessage rm : messages) {
            try {
                String payload = new String(rm.getMessage().decodeData(), StandardCharsets.UTF_8);
                handler.accept(payload);
                ackIds.add(rm.getAckId());
            } catch (Exception e) {
                log.error("Failed to process message ackId={} on {}", rm.getAckId(), fullSubscription, e);
            }
        }
        acknowledge(fullSubscription, ackIds);
    }

    private void acknowledge(String fullSubscription, List<String> ackIds) {
        if (ackIds.isEmpty()) return;
        try {
            AcknowledgeRequest request = new AcknowledgeRequest().setAckIds(ackIds);
            pubsub.projects().subscriptions().acknowledge(fullSubscription, request).execute();
        } catch (IOException e) {
            log.warn("Acknowledge on {} failed: {}", fullSubscription, e.getMessage());
        }
    }

    /** Create the topic if it doesn't already exist (idempotent). */
    public void getOrCreateTopic(String fullTopic) {
        try {
            pubsub.projects().topics().get(fullTopic).execute();
        } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() == HttpStatusCodes.STATUS_CODE_NOT_FOUND) {
                createTopic(fullTopic);
            } else {
                throw new IllegalStateException("getOrCreateTopic " + fullTopic + " failed", e);
            }
        } catch (IOException e) {
            throw new IllegalStateException("getOrCreateTopic " + fullTopic + " failed", e);
        }
    }

    private void createTopic(String fullTopic) {
        try {
            pubsub.projects().topics().create(fullTopic, new Topic().setName(fullTopic)).execute();
        } catch (IOException e) {
            throw new IllegalStateException("createTopic " + fullTopic + " failed", e);
        }
    }

    /** Create the pull subscription bound to {@code fullTopic} if it doesn't exist (idempotent). */
    public void getOrCreateSubscription(String fullTopic, String fullSubscription, int ackDeadlineSeconds) {
        try {
            pubsub.projects().subscriptions().get(fullSubscription).execute();
        } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() == HttpStatusCodes.STATUS_CODE_NOT_FOUND) {
                createSubscription(fullTopic, fullSubscription, ackDeadlineSeconds);
            } else {
                throw new IllegalStateException("getOrCreateSubscription " + fullSubscription + " failed", e);
            }
        } catch (IOException e) {
            throw new IllegalStateException("getOrCreateSubscription " + fullSubscription + " failed", e);
        }
    }

    private void createSubscription(String fullTopic, String fullSubscription, int ackDeadlineSeconds) {
        Subscription subscription = new Subscription()
                .setTopic(fullTopic)
                .setAckDeadlineSeconds(ackDeadlineSeconds);
        try {
            pubsub.projects().subscriptions().create(fullSubscription, subscription).execute();
        } catch (IOException e) {
            throw new IllegalStateException("createSubscription " + fullSubscription + " failed", e);
        }
    }
}
