package com.example.notifications;

import com.example.common.PubSubConfig;
import com.example.common.PubSubService;
import jakarta.ejb.Schedule;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A minimal external-client stand-in: consumes only, never publishes —
 * a notification service (email/SMS) reacting to payment events from its own
 * consumer groups on {@code payments.completed} and {@code payments.refunded}.
 */
@Singleton
@Startup
public class NotificationPoller {

    private static final Logger log = LoggerFactory.getLogger(NotificationPoller.class);

    @Inject
    PubSubService pubsub;

    @Schedule(second = "*/3", minute = "*", hour = "*", persistent = false)
    public void pollCompleted() {
        pubsub.consume(PubSubConfig.subscription(PubSubConfig.PAYMENTS_COMPLETED_NOTIFICATIONS_SUB), 25, payload -> log.info("Notifying customer: payment completed — {}", payload));
    }

    @Schedule(second = "*/3", minute = "*", hour = "*", persistent = false)
    public void pollRefunded() {
        pubsub.consume(PubSubConfig.subscription(PubSubConfig.PAYMENTS_REFUNDED_NOTIFICATIONS_SUB), 25, payload -> log.info("Notifying customer: payment refunded — {}", payload));
    }
}
