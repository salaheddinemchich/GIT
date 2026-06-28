package com.example.orders;

import com.example.common.PubSubClients;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.pubsub.v1.PubsubMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reacts to payment completion by marking the order PAID — orders' own
 * consumer group on the payments.completed topic, alongside the
 * notifications domain's {@code NotificationSubscriber}.
 */
@ApplicationScoped
public class PaymentCompletedSubscriber {

    private static final Logger log = LoggerFactory.getLogger(PaymentCompletedSubscriber.class);

    @Inject
    OrderStore orderStore;

    private Subscriber subscriber;

    public void start() {
        subscriber = PubSubClients.tryStartSubscriber(PubSubConfig.PAYMENTS_COMPLETED_SUBSCRIPTION, this::handle, log);
    }

    public void stop() {
        PubSubClients.stopQuietly(subscriber);
    }

    private void handle(PubsubMessage message, AckReplyConsumer consumer) {
        PaymentCompletedEvent event = PaymentCompletedEvent.fromMessage(message.getData().toStringUtf8());
        orderStore.markPaid(event.orderId());
        log.info("Order {} marked PAID (payment {})", event.orderId(), event.paymentId());
        consumer.ack();
    }
}
