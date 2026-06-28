package com.example.orders;

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
 * Publishes orders to a Pub/Sub topic via the idiomatic
 * {@code com.google.cloud.pubsub.v1.Publisher} client — the Jakarta EE
 * equivalent of kb-test's {@code PubSubTemplate}-based publish path.
 */
@ApplicationScoped
public class OrderPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderPublisher.class);

    private Publisher publisher;

    @PostConstruct
    void init() {
        publisher = PubSubClients.tryBuildPublisher(PubSubConfig.ORDERS_TOPIC, log);
    }

    @PreDestroy
    void shutdown() {
        PubSubClients.shutdownQuietly(publisher);
    }

    public void publish(Order order) {
        if (publisher == null) {
            throw new IllegalStateException("Publisher for topic " + PubSubConfig.ORDERS_TOPIC + " is unavailable "
                    + "— set PUBSUB_EMULATOR_HOST or configure GCP credentials");
        }
        log.info("Publishing order {} to topic {}", order.id(), PubSubConfig.ORDERS_TOPIC);
        PubsubMessage message = PubsubMessage.newBuilder()
                .setData(ByteString.copyFromUtf8(order.toMessage()))
                .build();
        try {
            publisher.publish(message).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new IllegalStateException("Failed to publish order " + order.id(), e);
        }
    }
}
