package com.example.orders;

import com.example.common.PubSubConfig;
import com.example.common.PubSubService;
import com.example.payments.Payment;
import jakarta.ejb.Schedule;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Orders domain's two Pub/Sub consumers, polled on EJB timers (the REST v1
 * client has no async push API — you pull). Mirrors test-altrix's
 * {@code OrderEventListener} polling style.
 *
 * <ul>
 *   <li>{@code orders.created.processor} — orders' own consumer; logs each order.</li>
 *   <li>{@code payments.completed.orders} — marks the order PAID when payment lands.</li>
 * </ul>
 */
@Singleton
@Startup
public class OrderEventsPoller {

    private static final Logger log = LoggerFactory.getLogger(OrderEventsPoller.class);

    @Inject
    PubSubService pubsub;
    @Inject
    OrderStore orderStore;

    @Schedule(second = "*/3", minute = "*", hour = "*", persistent = false)
    public void pollOrders() {
        pubsub.consume(PubSubConfig.subscription(PubSubConfig.ORDERS_PROCESSOR_SUB), 25, payload -> {
            Order order = Order.fromMessage(payload);
            log.info("Processing order {} ({} x{})", order.id(), order.product(), order.quantity());
        });
    }

    @Schedule(second = "*/3", minute = "*", hour = "*", persistent = false)
    public void pollPaymentsCompleted() {
        pubsub.consume(PubSubConfig.subscription(PubSubConfig.PAYMENTS_COMPLETED_ORDERS_SUB), 25, payload -> {
            Payment payment = Payment.fromMessage(payload);
            orderStore.markPaid(payment.orderId());
            log.info("Order {} marked PAID (payment {})", payment.orderId(), payment.id());
        });
    }
}
