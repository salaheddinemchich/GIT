package com.example.payments;

import com.example.common.PubSubClients;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.pubsub.v1.PubsubMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Payments' own consumer group on the orders.created topic — takes payment
 * for every order placed and publishes the result. Idiomatic pull
 * subscriber, the same path {@code com.example.orders.OrderSubscriber} uses.
 */
@ApplicationScoped
public class PaymentRequestSubscriber {

    private static final Logger log = LoggerFactory.getLogger(PaymentRequestSubscriber.class);
    private static final double UNIT_PRICE = 9.99;

    @Inject
    PaymentStore paymentStore;
    @Inject
    PaymentPublisher paymentPublisher;

    private Subscriber subscriber;

    public void start() {
        subscriber = PubSubClients.tryStartSubscriber(PubSubConfig.ORDERS_CREATED_SUBSCRIPTION, this::handle, log);
    }

    public void stop() {
        PubSubClients.stopQuietly(subscriber);
    }

    private void handle(PubsubMessage message, AckReplyConsumer consumer) {
        OrderCreatedEvent order = OrderCreatedEvent.fromMessage(message.getData().toStringUtf8());
        Payment payment = new Payment(UUID.randomUUID().toString(), order.id(),
                order.quantity() * UNIT_PRICE, "COMPLETED");
        paymentStore.save(payment);
        paymentPublisher.publishCompleted(payment);
        log.info("Took payment {} for order {}", payment.id(), order.id());
        consumer.ack();
    }
}
