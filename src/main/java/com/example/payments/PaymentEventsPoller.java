package com.example.payments;

import com.example.common.PubSubConfig;
import com.example.common.PubSubService;
import com.example.orders.Order;
import jakarta.ejb.Schedule;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Payments domain's consumer on {@code orders.created.payments} (fan-out #2 on
 * the orders.created topic): takes payment for every order placed and
 * publishes the result to {@code payments.completed}.
 */
@Singleton
@Startup
public class PaymentEventsPoller {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventsPoller.class);
    private static final double UNIT_PRICE = 9.99;

    @Inject
    PubSubService pubsub;
    @Inject
    PaymentStore paymentStore;

    @Schedule(second = "*/3", minute = "*", hour = "*", persistent = false)
    public void pollOrders() {
        pubsub.consume(PubSubConfig.subscription(PubSubConfig.ORDERS_PAYMENTS_SUB), 25, payload -> {
            Order order = Order.fromMessage(payload);
            Payment payment = new Payment(UUID.randomUUID().toString(), order.id(), order.quantity() * UNIT_PRICE, "COMPLETED");
            paymentStore.save(payment);
            pubsub.publish(PubSubConfig.topic(PubSubConfig.PAYMENTS_COMPLETED_TOPIC), payment.toMessage());
            log.info("Took payment {} for order {}", payment.id(), order.id());
        });
    }
}
