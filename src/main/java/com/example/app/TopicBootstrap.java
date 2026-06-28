package com.example.app;

import com.example.common.PubSubConfig;
import com.example.common.PubSubService;
import jakarta.annotation.PostConstruct;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.example.common.PubSubConfig.subscription;
import static com.example.common.PubSubConfig.topic;

/**
 * Creates every topic + subscription the app needs at boot. Best-effort: a
 * Pub/Sub outage (no emulator, no GCP credentials) is logged and ignored so
 * the app still starts — the scheduled pollers keep retrying, and only
 * Pub/Sub-dependent behaviour is affected.
 */
@Singleton
@Startup
public class TopicBootstrap {

    private static final Logger log = LoggerFactory.getLogger(TopicBootstrap.class);

    /** Default pull-subscription ack deadline, in seconds. */
    private static final int ACK_DEADLINE = 30;

    @Inject
    PubSubService pubsub;

    @PostConstruct
    void init() {
        log.info("Bootstrapping Pub/Sub topics + subscriptions...");
        try {
            pubsub.getOrCreateTopic(topic(PubSubConfig.ORDERS_TOPIC));
            pubsub.getOrCreateTopic(topic(PubSubConfig.PAYMENTS_COMPLETED_TOPIC));
            pubsub.getOrCreateTopic(topic(PubSubConfig.PAYMENTS_REFUNDED_TOPIC));
            pubsub.getOrCreateSubscription(topic(PubSubConfig.ORDERS_TOPIC), subscription(PubSubConfig.ORDERS_PROCESSOR_SUB), ACK_DEADLINE);
            pubsub.getOrCreateSubscription(topic(PubSubConfig.ORDERS_TOPIC), subscription(PubSubConfig.ORDERS_PAYMENTS_SUB), ACK_DEADLINE);
            pubsub.getOrCreateSubscription(topic(PubSubConfig.PAYMENTS_COMPLETED_TOPIC), subscription(PubSubConfig.PAYMENTS_COMPLETED_ORDERS_SUB), ACK_DEADLINE);
            pubsub.getOrCreateSubscription(topic(PubSubConfig.PAYMENTS_COMPLETED_TOPIC), subscription(PubSubConfig.PAYMENTS_COMPLETED_NOTIFICATIONS_SUB), ACK_DEADLINE);
            pubsub.getOrCreateSubscription(topic(PubSubConfig.PAYMENTS_REFUNDED_TOPIC), subscription(PubSubConfig.PAYMENTS_REFUNDED_NOTIFICATIONS_SUB), ACK_DEADLINE);
            log.info("Pub/Sub topics + subscriptions ready.");
        } catch (Exception e) {
            log.warn("Skipping Pub/Sub bootstrap ({}). Pollers will keep retrying; " + "only Pub/Sub-dependent endpoints are affected.", e.getMessage());
        }
    }
}
