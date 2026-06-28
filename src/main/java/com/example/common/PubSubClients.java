package com.example.common;

import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.rpc.FixedTransportChannelProvider;
import com.google.api.gax.rpc.TransportChannelProvider;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.TopicName;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.slf4j.Logger;

import java.io.IOException;

/**
 * Shared Pub/Sub client plumbing — the rough equivalent of what
 * {@code spring-cloud-gcp-starter-pubsub}'s auto-configuration gives kb-test
 * for free. One anchor per concern (project id, emulator wiring, publisher/
 * subscriber construction) so the Kafka migration has a small, well-defined
 * surface to replace.
 *
 * <p>Honours {@code PUBSUB_EMULATOR_HOST} the same way
 * {@code test-altrix}'s {@code PubsubClientProducer} does, so the whole
 * choreography can run against the local {@code altrix-pubsub-emulator}
 * container without real GCP credentials.
 */
public final class PubSubClients {

    public static final String PROJECT_ID = System.getProperty("gcp.project.id",
            System.getenv().getOrDefault("GCP_PROJECT_ID", "altrix-local"));

    private PubSubClients() {
    }

    public static TopicName topicName(String topicId) {
        return TopicName.of(PROJECT_ID, topicId);
    }

    public static ProjectSubscriptionName subscriptionName(String subscriptionId) {
        return ProjectSubscriptionName.of(PROJECT_ID, subscriptionId);
    }

    public static Publisher buildPublisher(String topicId) throws IOException {
        Publisher.Builder builder = Publisher.newBuilder(topicName(topicId));
        TransportChannelProvider emulatorChannel = emulatorChannelProvider();
        if (emulatorChannel != null) {
            builder.setChannelProvider(emulatorChannel)
                   .setCredentialsProvider(NoCredentialsProvider.create());
        }
        return builder.build();
    }

    public static Subscriber buildSubscriber(String subscriptionId, MessageReceiver receiver) {
        Subscriber.Builder builder = Subscriber.newBuilder(subscriptionName(subscriptionId), receiver);
        TransportChannelProvider emulatorChannel = emulatorChannelProvider();
        if (emulatorChannel != null) {
            builder.setChannelProvider(emulatorChannel)
                   .setCredentialsProvider(NoCredentialsProvider.create());
        }
        return builder.build();
    }

    /** Non-null only when {@code PUBSUB_EMULATOR_HOST} is set — e.g. {@code localhost:8085}. */
    private static TransportChannelProvider emulatorChannelProvider() {
        String emulatorHost = System.getenv("PUBSUB_EMULATOR_HOST");
        if (emulatorHost == null || emulatorHost.isBlank()) return null;
        ManagedChannel channel = ManagedChannelBuilder.forTarget(emulatorHost).usePlaintext().build();
        return FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel));
    }

    // ── graceful degradation ────────────────────────────────────────────────
    //
    // Unlike test-altrix's legacy REST v1 Pubsub.Builder (which resolves
    // credentials lazily, only failing when an actual HTTP call is made),
    // the modern gRPC Publisher/Subscriber builders resolve Application
    // Default Credentials EAGERLY inside build()/startAsync() — so without
    // PUBSUB_EMULATOR_HOST set or real GCP credentials configured, every
    // build attempt throws immediately. An @ApplicationScoped bean's
    // @PostConstruct (publishers) or a ServletContextListener.contextInitialized
    // (subscribers, via AppStartupListener) throwing crashes the ENTIRE
    // webapp deployment, not just messaging — taking down even unrelated
    // endpoints like /api/health. These helpers contain that failure to
    // messaging only, mirroring test-altrix's documented "app still boots,
    // messaging calls fail until configured" behaviour.

    /** Returns {@code null} (never throws) when the publisher can't be built — e.g. no emulator/credentials. */
    public static Publisher tryBuildPublisher(String topicId, Logger log) {
        try {
            return buildPublisher(topicId);
        } catch (Exception e) {
            log.warn("Publisher for topic '{}' unavailable ({}). Set PUBSUB_EMULATOR_HOST to run against "
                    + "the local emulator, or configure GCP credentials. Publishing to this topic will fail "
                    + "until then; the rest of the app still boots.", topicId, e.getMessage());
            return null;
        }
    }

    /** No-op if {@code publisher} is {@code null} (never built) or already shut down. */
    public static void shutdownQuietly(Publisher publisher) {
        if (publisher == null) return;
        try {
            publisher.shutdown();
        } catch (Exception ignored) {
            // best-effort
        }
    }

    /** Returns {@code null} (never throws) when the subscriber can't be started — e.g. no emulator/credentials. */
    public static Subscriber tryStartSubscriber(String subscriptionId, MessageReceiver receiver, Logger log) {
        try {
            Subscriber subscriber = buildSubscriber(subscriptionId, receiver);
            subscriber.startAsync().awaitRunning();
            log.info("Subscribed to {}", subscriptionId);
            return subscriber;
        } catch (Exception e) {
            log.warn("Subscriber for '{}' unavailable ({}). Set PUBSUB_EMULATOR_HOST to run against the "
                    + "local emulator, or configure GCP credentials. This subscription will not receive "
                    + "messages until then; the rest of the app still boots.", subscriptionId, e.getMessage());
            return null;
        }
    }

    /** No-op if {@code subscriber} is {@code null} (never started). */
    public static void stopQuietly(Subscriber subscriber) {
        if (subscriber == null) return;
        try {
            subscriber.stopAsync().awaitTerminated();
        } catch (Exception ignored) {
            // best-effort — may never have reached RUNNING
        }
    }
}
