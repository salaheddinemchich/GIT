# orders-jakarta-pubsub — Altrix migration validation fixture

A **minimal Jakarta EE 10 (JAX-RS + CDI + EJB) orders/payments/notifications
fixture** using the **legacy Google Cloud Pub/Sub REST v1 client**
(`com.google.api.services.pubsub`) — the same client style the sibling
`test-altrix` fixture uses, deliberately **not** the gRPC-based
`com.google.cloud:google-cloud-pubsub` client.

Pure HTTP/JSON Pub/Sub: publish/pull/ack via `pubsub.projects().topics()...`
and `pubsub.projects().subscriptions()...`, with consumers driven by EJB
`@Schedule` pollers (the REST v1 client has no async push API — you pull).
Kept small on purpose so the migration has a small, clean surface to rewrite.

## Domains (one deployable, three bounded contexts)

- **`com.example.orders`** — places an order, then reacts when it's paid.
- **`com.example.payments`** — takes payment for every order, and handles refunds.
- **`com.example.notifications`** — consumer-only "client": notifies on payment events, never publishes.

`com.example.common` holds the entire Pub/Sub seam: `PubSubConfig` (all topic
+ subscription names), `PubsubClientProducer` (CDI `@Produces` the REST v1
`Pubsub` client, with `PUBSUB_EMULATOR_HOST` support + graceful degradation),
and `PubSubService` (publish / consume / ack / topic+subscription
provisioning). `com.example.app` hosts the JAX-RS bootstrap
(`JaxRsApplication`, `/api/health`) and `TopicBootstrap` (creates all topics +
subscriptions at startup, best-effort).

## Choreography

```
POST /api/orders              OrderResource → publish orders.created
                                   │
                                   ▼
                         topic: orders.created
                  ┌────────────────┴────────────────┐
                  ▼                                  ▼
   orders.created.processor              orders.created.payments
   (OrderEventsPoller, orders)            (PaymentEventsPoller, payments)
                                                      │
                                                      ▼
                                       topic: payments.completed
                          ┌───────────────────────────┴───────────────────────────┐
                          ▼                                                       ▼
            payments.completed.orders                              payments.completed.notifications
            (OrderEventsPoller, orders)                            (NotificationPoller, notifications)
            → marks the order PAID                                  → "send" a notification

POST /api/payments/{orderId}/refund   PaymentResource → publish payments.refunded
                                          │
                                          ▼
                              topic: payments.refunded
                                          │
                                          ▼
                          payments.refunded.notifications
                          (NotificationPoller, notifications)
```

3 topics, 5 subscriptions, 2 topics with fan-out to two independent consumer
groups — a realistic topic→Kafka-topic / subscription→consumer-group mapping
shape.

## REST endpoints
- `GET /api/health` — liveness probe
- `POST /api/orders` — place an order (publishes `orders.created`)
- `GET /api/orders/{id}/status` — `CREATED` or `PAID`
- `GET /api/payments/{orderId}` — look up the payment taken for an order
- `POST /api/payments/{orderId}/refund` — refund it (publishes `payments.refunded`)

## What a correct Spring-Kafka-hybrid migration looks like
| Pub/Sub (this project) | Kafka (Spring Kafka hybrid target) |
|---|---|
| `pubsub.projects().topics().publish(topic, req)` | `kafkaTemplate.send(topic, payload)` |
| `pubsub.projects().subscriptions().pull(...)` + `@Schedule` poller | `@KafkaListener(topics=..., groupId=...)` method |
| `pubsub.projects().subscriptions().acknowledge(...)` | (handled by the listener container's offset commit) |
| `com.google.api.services.pubsub.Pubsub` (REST v1 client) | `KafkaTemplate` / `@KafkaListener` (via `SpringBeanBridge` / `CdiLookup`) |
| `google-api-services-pubsub` Maven dependency | `spring-kafka` + `spring-context` |
| one subscription per topic per consumer | one `groupId` per topic per consumer — same fan-out shape |

## Build

`mvn` is not installed locally — build via the same Docker image the Altrix
sandbox uses:
```bash
docker run --rm -e MAVEN_OPTS="-Xmx2g" -v "C:/Users/SALAH/Projects/test-altrix-jakarta-pubsub:/workspace" -v "C:/Users/SALAH/.m2:/root/.m2" -w /workspace maven:3.9-eclipse-temurin-21-alpine mvn -B clean package -DskipTests
```

## Run
```bash
java -jar target/orders-jakarta-pubsub-microbundle.jar --port 8081
```
(Payara Micro's `--port` flag goes *after* the jar, space-separated — not
`--server.port=`, which is Spring Boot's syntax.)

Set `PUBSUB_EMULATOR_HOST` (and optionally `GCP_PROJECT_ID`) before starting,
pointed at the `altrix-pubsub-emulator` container from
`backend/docker-compose.yml`. `TopicBootstrap` auto-creates all 3 topics + 5
subscriptions at startup, so no manual provisioning is needed. See `E2E.ps1`
for a full build + run + smoke-test script.

## Run through Altrix
Upload this project, select **Jakarta EE + Spring Kafka hybrid** as the
migration target, run the migration, then compare the output against the
table above.
