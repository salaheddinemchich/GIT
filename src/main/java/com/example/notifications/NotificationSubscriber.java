package com.example.notifications;

import com.example.common.PubSubClients;
import com.google.cloud.pubsub.v1.Subscriber;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A minimal external-client stand-in: it only consumes, never publishes,
 * representing a notification service (email/SMS) reacting to payment
 * events from its own consumer groups.
 */
@ApplicationScoped
public class NotificationSubscriber {

    private static final Logger log = LoggerFactory.getLogger(NotificationSubscriber.class);

    private Subscriber completedSubscriber;
    private Subscriber refundedSubscriber;

    public void start() {
        completedSubscriber = PubSubClients.tryStartSubscriber(PubSubConfig.PAYMENTS_COMPLETED_SUBSCRIPTION,
                (message, consumer) -> {
                    log.info("Notifying customer: payment completed — {}", message.getData().toStringUtf8());
                    consumer.ack();
                }, log);
        refundedSubscriber = PubSubClients.tryStartSubscriber(PubSubConfig.PAYMENTS_REFUNDED_SUBSCRIPTION,
                (message, consumer) -> {
                    log.info("Notifying customer: payment refunded — {}", message.getData().toStringUtf8());
                    consumer.ack();
                }, log);
    }

    public void stop() {
        PubSubClients.stopQuietly(completedSubscriber);
        PubSubClients.stopQuietly(refundedSubscriber);
    }
}
