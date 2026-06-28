package com.example.orders;

/**
 * Minimal order payload published to Pub/Sub as pipe-delimited text — same
 * wire format as kb-test's {@code com.example.orders.Order}.
 */
public record Order(String id, String product, int quantity) {

    /** Naive serialisation — fine for a sample; the messaging mechanics are the point. */
    public String toMessage() {
        return id + "|" + product + "|" + quantity;
    }

    public static Order fromMessage(String message) {
        String[] parts = message.split("\\|");
        return new Order(parts[0], parts[1], Integer.parseInt(parts[2]));
    }
}
