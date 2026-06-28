package com.example.common;

/**
 * Every Pub/Sub topic and subscription name the app uses, in one place — the
 * single anchor the Kafka migration updates when porting topics to Kafka
 * topics and subscriptions to consumer groups.
 *
 * <p>{@link #topic(String)} / {@link #subscription(String)} build the
 * fully-qualified REST v1 resource paths ({@code projects/{id}/topics/{name}}
 * and {@code projects/{id}/subscriptions/{name}}) the
 * {@code com.google.api.services.pubsub} client expects.
 */
public final class PubSubConfig {

    public static final String PROJECT_ID = System.getProperty("gcp.project.id",
            System.getenv().getOrDefault("GCP_PROJECT_ID", "altrix-local"));

    // ── Topics ───────────────────────────────────────────────────────────────
    public static final String ORDERS_TOPIC = "orders.created";
    public static final String PAYMENTS_COMPLETED_TOPIC = "payments.completed";
    public static final String PAYMENTS_REFUNDED_TOPIC = "payments.refunded";

    // ── Subscriptions ────────────────────────────────────────────────────────
    /** Orders' own consumer on orders.created. */
    public static final String ORDERS_PROCESSOR_SUB = "orders.created.processor";
    /** Payments' consumer on orders.created — fan-out #2 on the same topic. */
    public static final String ORDERS_PAYMENTS_SUB = "orders.created.payments";
    /** Orders' consumer on payments.completed — marks the order PAID. */
    public static final String PAYMENTS_COMPLETED_ORDERS_SUB = "payments.completed.orders";
    /** Notifications' consumer on payments.completed — fan-out #2. */
    public static final String PAYMENTS_COMPLETED_NOTIFICATIONS_SUB = "payments.completed.notifications";
    /** Notifications' consumer on payments.refunded. */
    public static final String PAYMENTS_REFUNDED_NOTIFICATIONS_SUB = "payments.refunded.notifications";

    /** {@code projects/{projectId}/topics/{topicId}} */
    public static String topic(String topicId) {
        return "projects/" + PROJECT_ID + "/topics/" + topicId;
    }

    /** {@code projects/{projectId}/subscriptions/{subscriptionId}} */
    public static String subscription(String subscriptionId) {
        return "projects/" + PROJECT_ID + "/subscriptions/" + subscriptionId;
    }

    private PubSubConfig() {
    }
}
