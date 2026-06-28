package com.example.app;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** Liveness probe — exposed at {@code /api/health}, the path Altrix's sandbox boot-health runner checks. */
@Path("/health")
public class HealthResource {

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response health() {
        return Response.ok("OK").build();
    }
}
