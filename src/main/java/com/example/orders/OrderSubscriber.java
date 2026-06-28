package com.example.orders;

import com.example.common.PubSubClients;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.pubsub.v1.PubsubMessage;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Subscribes to the orders subscription and processes each message, acking
 * on success — the idiomatic {@code com.google.cloud.pubsub.v1.Subscriber}
 * pull path, Jakarta EE's equivalent of kb-test's
 * {@code pubSubTemplate.subscribe(...)}.
 *
 * <p>Started/stopped explicitly by {@link com.example.app.AppStartupListener}
 * rather than via {@code @PostConstruct} — an {@code @ApplicationScoped}
 * CDI bean's {@code @PostConstruct} only fires on first contextual use, not
 * eagerly at deployment, so something has to force that first use.
 */
@ApplicationScoped
public class OrderSubscriber {

    private static final Logger log = LoggerFactory.getLogger(OrderSubscriber.class);

    private Subscriber subscriber;

    public void start() {
        subscriber = PubSubClients.tryStartSubscriber(PubSubConfig.ORDERS_SUBSCRIPTION, this::handle, log);
    }

    public void stop() {
        PubSubClients.stopQuietly(subscriber);
    }

    private void handle(PubsubMessage message, AckReplyConsumer consumer) {
        Order order = Order.fromMessage(message.getData().toStringUtf8());
        log.info("Processing order {} ({} x{})", order.id(), order.product(), order.quantity());
        consumer.ack();
    }
}
