package com.example.app;

import com.example.notifications.NotificationSubscriber;
import com.example.orders.OrderSubscriber;
import com.example.orders.PaymentCompletedSubscriber;
import com.example.payments.PaymentRequestSubscriber;
import jakarta.inject.Inject;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Starts every Pub/Sub subscriber when the app boots, stops them on
 * shutdown. CDI {@code @ApplicationScoped} beans don't run any startup
 * logic on their own just by being injected — this listener is the explicit
 * "wire everything up" anchor that {@code spring-cloud-gcp-starter-pubsub}'s
 * auto-configuration + each bean's own {@code @PostConstruct} gives kb-test
 * for free.
 */
@WebListener
public class AppStartupListener implements ServletContextListener {

    private static final Logger log = LoggerFactory.getLogger(AppStartupListener.class);

    @Inject
    OrderSubscriber orderSubscriber;
    @Inject
    PaymentCompletedSubscriber paymentCompletedSubscriber;
    @Inject
    PaymentRequestSubscriber paymentRequestSubscriber;
    @Inject
    NotificationSubscriber notificationSubscriber;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        log.info("Starting Pub/Sub subscribers...");
        orderSubscriber.start();
        paymentCompletedSubscriber.start();
        paymentRequestSubscriber.start();
        notificationSubscriber.start();
        log.info("All Pub/Sub subscribers started.");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        notificationSubscriber.stop();
        paymentRequestSubscriber.stop();
        paymentCompletedSubscriber.stop();
        orderSubscriber.stop();
    }
}
