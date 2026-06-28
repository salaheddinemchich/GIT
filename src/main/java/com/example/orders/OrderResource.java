package com.example.orders;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * REST entry points to publish an order and check its status — lets the
 * messaging path (including the payments round-trip) be exercised end to
 * end. JAX-RS equivalent of kb-test's {@code OrderController}.
 */
@Path("/orders")
public class OrderResource {

    @Inject
    OrderPublisher publisher;
    @Inject
    OrderStore orderStore;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public Response create(Order order) {
        orderStore.markCreated(order.id());
        publisher.publish(order);
        return Response.ok("published " + order.id()).build();
    }

    @GET
    @Path("/{id}/status")
    @Produces(MediaType.TEXT_PLAIN)
    public Response status(@PathParam("id") String id) {
        OrderStore.Status status = orderStore.statusOf(id);
        if (status == null) return Response.status(Response.Status.NOT_FOUND).build();
        return Response.ok(status.name()).build();
    }
}
