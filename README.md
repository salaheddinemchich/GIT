# orders-jakarta-pubsub — Altrix migration validation fixture

A **Jakarta EE 10 (JAX-RS + CDI) port of the sibling `kb-test` project**:
same orders/payments/notifications domain, same choreography, same
idiomatic Pub/Sub usage style — just on Jakarta EE instead of Spring Boot,
using the modern `com.google.cloud:google-cloud-pubsub` client instead of
Spring Cloud GCP's `PubSubTemplate`.

Unlike `test-altrix` (a hand-rolled legacy REST v1 Pub/Sub *framework* — the
hardest possible input) and `kb-test` (idiomatic, but Spring Boot), this is
the **idiomatic Jakarta EE case**: direct, conventional Pub/Sub usage, no
custom abstraction layer, ready to validate the Jakarta EE → Spring Kafka
hybrid migration target end-to-end on a clean baseline before stress-testing
it against `test-altrix`'s harder input.

## Domains (one deployable, three bounded contexts)

- **`com.example.orders`** — places an order, then reacts when it's paid.
- **`com.example.payments`** — takes payment for every order, and handles refunds.
- **`com.example.notifications`** — consumer-only "client": notifies on payment events, never publishes.

Each domain owns its own message DTOs (`Order`, `OrderCreatedEvent`,
`Payment`, `PaymentCompletedEvent`) even where they describe the same wire
message — intentional duplication, matching how independently-deployed
services would actually share a Pub/Sub contract. `com.example.app` hosts
the JAX-RS bootstrap (`JaxRsApplication`, `/api/health`) and
`AppStartupListener`, which starts/stops every subscriber on deploy/undeploy.
`com.example.common.PubSubClients` is the shared publisher/subscriber
builder (project id resolution + `PUBSUB_EMULATOR_HOST` wiring) — the rough
equivalent of what `spring-cloud-gcp-starter-pubsub`'s auto-configuration
gives kb-test for free.

## Choreography

```
POST /api/orders              OrderResource → OrderPublisher
                                   │
                                   ▼
                         topic: orders.created
                  ┌────────────────┴────────────────┐
                  ▼                                  ▼
   orders.created.processor              orders.created.payments
   (OrderSubscriber, orders)              (PaymentRequestSubscriber, payments)
                                                      │
                                                      ▼
                                       topic: payments.completed
                          ┌───────────────────────────┴───────────────────────────┐
                          ▼                                                       ▼
            payments.completed.orders                              payments.completed.notifications
            (PaymentCompletedSubscriber, orders)                    (NotificationSubscriber, notifications)
            → marks the order PAID                                  → "send" a notification

POST /api/payments/{orderId}/refund   PaymentResource → PaymentPublisher
                                          │
                                          ▼
                              topic: payments.refunded
                                          │
                                          ▼
                          payments.refunded.notifications
                          (NotificationSubscriber, notifications)
```

3 topics, 5 subscriptions, 2 topics with fan-out to two independent consumer
groups — same realistic topic→Kafka-topic / subscription→consumer-group
mapping shape as `kb-test`.

## REST endpoints
- `GET /api/health` — liveness probe
- `POST /api/orders` — place an order (publishes `orders.created`)
- `GET /api/orders/{id}/status` — `CREATED` or `PAID`
- `GET /api/payments/{orderId}` — look up the payment taken for an order
- `POST /api/payments/{orderId}/refund` — refund it (publishes `payments.refunded`)

## What a correct Spring-Kafka-hybrid migration looks like
| Pub/Sub (this project) | Kafka (Spring Kafka hybrid target) |
|---|---|
| `com.google.cloud.pubsub.v1.Publisher` | `KafkaTemplate<String,String>` (via `SpringBeanBridge`) |
| `publisher.publish(message).get()` | `kafkaTemplate.send(topic, payload)` |
| `com.google.cloud.pubsub.v1.Subscriber` + `MessageReceiver` | `@KafkaListener(topics=..., groupId=...)` method |
| `AppStartupListener` (manual subscriber lifecycle) | `SpringContextBootstrapper` (manual `ApplicationContext` lifecycle) |
| `google-cloud-pubsub` Maven dependency | `spring-kafka` + `spring-context` |
| one subscription per topic per consumer | one `groupId` per topic per consumer — same fan-out shape |

## Build

`mvn` is not installed locally — build via the same Docker image the Altrix
sandbox uses:
```bash
docker run --rm -v "C:/Users/SALAH/Projects/test-altrix-jakarta-pubsub:/workspace" -v "C:/Users/SALAH/.m2:/root/.m2" -w /workspace maven:3.9-eclipse-temurin-21-alpine mvn -B clean package -DskipTests
```

## Run
```bash
java -jar target/orders-jakarta-pubsub-microbundle.jar --port 8081
```
(Payara Micro's `--port` flag goes *after* the jar, space-separated — not
`--server.port=`, which is Spring Boot's syntax.)

To exercise real messaging, set `PUBSUB_EMULATOR_HOST` (and optionally
`GCP_PROJECT_ID`) before starting, pointed at the `altrix-pubsub-emulator`
container from `backend/docker-compose.yml`, and pre-create the 3 topics /
5 subscriptions listed above via the emulator's REST API or `gcloud`.

## Run through Altrix
Upload this project, select **Jakarta EE + Spring Kafka hybrid** as the
migration target, run the migration, then compare the output against the
table above.
