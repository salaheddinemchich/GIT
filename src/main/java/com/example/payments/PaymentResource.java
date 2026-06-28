package com.example.payments;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * REST entry points to inspect and refund payments — lets the payments side
 * of the choreography be exercised end to end, same spirit as
 * {@code com.example.orders.OrderResource}.
 */
@Path("/payments")
public class PaymentResource {

    @Inject
    PaymentStore paymentStore;
    @Inject
    PaymentPublisher paymentPublisher;

    @GET
    @Path("/{orderId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response byOrder(@PathParam("orderId") String orderId) {
        Payment payment = paymentStore.findByOrderId(orderId);
        if (payment == null) return Response.status(Response.Status.NOT_FOUND).build();
        return Response.ok(payment).build();
    }

    @POST
    @Path("/{orderId}/refund")
    @Produces(MediaType.TEXT_PLAIN)
    public Response refund(@PathParam("orderId") String orderId) {
        Payment payment = paymentStore.findByOrderId(orderId);
        if (payment == null) return Response.status(Response.Status.NOT_FOUND).build();
        Payment refunded = new Payment(payment.id(), payment.orderId(), payment.amount(), "REFUNDED");
        paymentStore.save(refunded);
        paymentPublisher.publishRefunded(refunded);
        return Response.ok("refunded " + orderId).build();
    }
}
