package com.example.common;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.pubsub.Pubsub;
import com.google.api.services.pubsub.PubsubScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CDI producer for the singleton {@link Pubsub} REST v1 client used by
 * {@link PubSubService}. Mirrors test-altrix's {@code PubsubClientProducer}.
 *
 * <p>If {@code PUBSUB_EMULATOR_HOST} (e.g. {@code localhost:8085}) is set, the
 * client points at the emulator and skips auth. Otherwise it uses Application
 * Default Credentials; if those aren't available it still returns a usable
 * client built without auth so the app boots cleanly and non-Pub/Sub
 * endpoints (e.g. {@code /api/health}) keep working — any real Pub/Sub call
 * fails at request time, which the pollers handle gracefully.
 */
@ApplicationScoped
public class PubsubClientProducer {

    private static final Logger log = LoggerFactory.getLogger(PubsubClientProducer.class);

    private static final String APP_NAME = "altrix-orders-jakarta-pubsub";

    /**
     * Produced as a {@code @Singleton} (pseudo-scope) rather than
     * {@code @ApplicationScoped}: Google's {@link Pubsub} is a concrete class
     * with no no-arg constructor, so CDI cannot build a normal-scoped proxy
     * for it ({@code WELD-001410}). A singleton is shared but not proxied —
     * one HTTP client for the whole application.
     */
    @Produces
    @Singleton
    public Pubsub createPubsubClient() {
        try {
            HttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();

            String emulatorHost = System.getenv("PUBSUB_EMULATOR_HOST");
            if (emulatorHost != null && !emulatorHost.isBlank()) {
                String rootUrl = "http://" + emulatorHost + "/";
                log.info("Initialising Pub/Sub REST v1 client against emulator at {}", rootUrl);
                return new Pubsub.Builder(transport, jsonFactory, request -> { /* no auth */ })
                        .setApplicationName(APP_NAME)
                        .setRootUrl(rootUrl)
                        .build();
            }

            log.info("Initialising Google Cloud Pub/Sub REST v1 client (real GCP endpoint)");
            HttpRequestInitializer requestInitializer;
            try {
                GoogleCredentials credentials = GoogleCredentials.getApplicationDefault()
                        .createScoped(PubsubScopes.PUBSUB);
                requestInitializer = new HttpCredentialsAdapter(credentials);
            } catch (Exception e) {
                log.warn("GCP credentials unavailable ({}). Pub/Sub client created in DEGRADED mode; "
                        + "any Pub/Sub call fails until credentials are configured or "
                        + "PUBSUB_EMULATOR_HOST is set.", e.getMessage());
                requestInitializer = request -> { /* no auth */ };
            }

            return new Pubsub.Builder(transport, jsonFactory, requestInitializer)
                    .setApplicationName(APP_NAME)
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build Pub/Sub REST v1 transport", e);
        }
    }
}
