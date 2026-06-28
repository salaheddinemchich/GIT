package com.example.payments;

import com.example.common.PubSubClients;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;

/**
 * Publishes payment lifecycle events via the idiomatic
 * {@code com.google.cloud.pubsub.v1.Publisher} client — the same publish
 * path {@code com.example.orders.OrderPublisher} uses, one {@link Publisher}
 * per topic since each is bound to a single {@code TopicName} at construction.
 */
@ApplicationScoped
public class PaymentPublisher {

    private static final Logger log = LoggerFactory.getLogger(PaymentPublisher.class);

    private Publisher completedPublisher;
    private Publisher refundedPublisher;

    @PostConstruct
    void init() {
        completedPublisher = PubSubClients.tryBuildPublisher(PubSubConfig.PAYMENTS_COMPLETED_TOPIC, log);
        refundedPublisher = PubSubClients.tryBuildPublisher(PubSubConfig.PAYMENTS_REFUNDED_TOPIC, log);
    }

    @PreDestroy
    void shutdown() {
        PubSubClients.shutdownQuietly(completedPublisher);
        PubSubClients.shutdownQuietly(refundedPublisher);
    }

    public void publishCompleted(Payment payment) {
        log.info("Publishing payment {} to topic {}", payment.id(), PubSubConfig.PAYMENTS_COMPLETED_TOPIC);
        publish(completedPublisher, PubSubConfig.PAYMENTS_COMPLETED_TOPIC, payment);
    }

    public void publishRefunded(Payment payment) {
        log.info("Publishing refund {} to topic {}", payment.id(), PubSubConfig.PAYMENTS_REFUNDED_TOPIC);
        publish(refundedPublisher, PubSubConfig.PAYMENTS_REFUNDED_TOPIC, payment);
    }

    private void publish(Publisher publisher, String topicId, Payment payment) {
        if (publisher == null) {
            throw new IllegalStateException("Publisher for topic " + topicId + " is unavailable "
                    + "— set PUBSUB_EMULATOR_HOST or configure GCP credentials");
        }
        PubsubMessage message = PubsubMessage.newBuilder()
                .setData(ByteString.copyFromUtf8(payment.toMessage()))
                .build();
        try {
            publisher.publish(message).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new IllegalStateException("Failed to publish payment " + payment.id(), e);
        }
    }
}
