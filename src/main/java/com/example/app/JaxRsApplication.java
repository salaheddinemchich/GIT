package com.example.app;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/**
 * No overrides — JAX-RS auto-discovers every {@code @Path}-annotated class
 * on the classpath when {@code getClasses()}/{@code getSingletons()} are
 * both left empty.
 */
@ApplicationPath("/api")
public class JaxRsApplication extends Application {
}
