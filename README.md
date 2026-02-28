# aegis-ai-producer-starter

[![Maven Central](https://img.shields.io/maven-central/v/io.github.girisenji.aegis/aegis-ai-producer-starter)](https://central.sonatype.com/artifact/io.github.girisenji.aegis/aegis-ai-producer-starter)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.x-brightgreen.svg)](https://spring.io/projects/spring-boot)

A zero-business-logic Spring Boot auto-configuration starter that wires a Logback `KafkaAppender` — with built-in PII masking — into any microservice. `ERROR`-level events are forwarded as structured JSON to a Kafka topic so the Aegis AI agent can consume and act on them.

---

## How it works

```
Your service throws an exception
         │
         ▼
Logback KafkaAppender  (AsyncAppender — never blocks)
         │
         │  MaskingMessageJsonProvider scrubs PII from the message field
         │  PiiMaskingConverter registers %maskedMsg for pattern layouts
         │
         ▼
Kafka topic: aegis-production-errors
         │
         ▼
Aegis AI Agent  →  GitHub PR with root-cause analysis + fix
```

- **Fire-and-forget** — `max.block.ms=0`, `acks=0`, `neverBlock=true`; Kafka outages never stall the calling thread.
- **PII masking** — EMAIL, Bearer/token values, and card numbers are redacted before bytes leave the JVM.
- **Zero wiring** — Spring Boot auto-configuration activates as soon as `KafkaAppender` is on the classpath and `aegis.enabled=true` (the default).

---

## Requirements

| Requirement | Version |
|---|---|
| Java | 21 |
| Spring Boot | 3.3+ |
| logback-kafka-appender | included transitively |

---

## Installation

Add the starter to your service's `pom.xml`:

```xml
<dependency>
  <groupId>io.github.girisenji.aegis</groupId>
  <artifactId>aegis-ai-producer-starter</artifactId>
  <version>0.1.0</version>
</dependency>
```

---

## Configuration

### `application.yml` (minimum required)

```yaml
aegis:
  repo-name: my-org/my-service           # GitHub repo — used by the Aegis agent to locate source
  kafka:
    bootstrap-servers: kafka-broker:9092 # comma-separated host:port list
```

### Full property reference

| Property | Default | Description |
|---|---|---|
| `aegis.enabled` | `true` | Set to `false` to disable all wiring (e.g. in test slices) |
| `aegis.repo-name` | _(empty)_ | GitHub `org/repo` identifier forwarded with every event |
| `aegis.kafka.bootstrap-servers` | _(required)_ | Kafka broker(s) |
| `aegis.kafka.topic` | `aegis-production-errors` | Target Kafka topic |
| `aegis.kafka.max-block-ms` | `0` | Producer block timeout — `0` means never block |
| `aegis.kafka.acks` | `0` | Producer ack mode — `0` = fire-and-forget |

---

## Logback wiring

Include the provided Logback fragment in your service's `logback-spring.xml` and add the `ASYNC_KAFKA` appender-ref to your root logger:

```xml
<configuration>
  <include resource="logback-spring-aegis.xml"/>

  <!-- your other appenders (CONSOLE, FILE, etc.) -->

  <root level="INFO">
    <appender-ref ref="CONSOLE"/>
    <appender-ref ref="ASYNC_KAFKA"/>   <!-- forwards ERROR+ events to Kafka -->
  </root>
</configuration>
```

> **Note:** Use `logback-spring.xml` (not `logback.xml`) so the `<springProperty>` elements resolve Spring Boot properties at startup.

---

## PII masking

The `PiiMaskingConverter` scrubs the following patterns from the log message field **before** it is serialised to JSON and sent to Kafka:

| Category | Example input | Output |
|---|---|---|
| Email | `user@corp.com` | `[EMAIL]` |
| Token / Bearer | `Authorization: Bearer eyJ...` | `[TOKEN]` |
| Card number | `4111 1111 1111 1111` | `[CARD]` |

PII masking is also applied to pattern-layout appenders (CONSOLE, FILE) via the `%maskedMsg` conversion word, which is registered automatically when the fragment is included.

---

## Disabling in tests

To prevent test slices from connecting to Kafka, set the property in your test configuration:

```yaml
# src/test/resources/application-test.yml
aegis:
  enabled: false
```

Or inline with `@SpringBootTest`:

```java
@SpringBootTest(properties = "aegis.enabled=false")
class MyServiceTest { ... }
```

---

## Kafka event schema

Every event published to the topic is a JSON object produced by `logstash-logback-encoder`:

```json
{
  "@timestamp":   "2026-02-28T10:00:00.000Z",
  "@version":     "1",
  "level":        "ERROR",
  "message":      "Payment failed: [CARD] declined",
  "logger_name":  "com.example.PaymentService",
  "thread_name":  "http-nio-8080-exec-1",
  "stack_trace":  "com.example.PaymentException: ...",
  "service":      "payment-service",
  "repoName":     "my-org/payment-service"
}
```

---

## Build & test

```bash
# Unit tests (55 tests, ~4 s)
mvn test

# Full verify: unit + integration tests + JaCoCo coverage gate (90% line / 85% branch)
mvn verify

# Integration tests require Docker (Testcontainers Kafka); skipped automatically if Docker is absent
# OWASP dependency-check (explicit profile)
mvn verify -Powasp
```

---

## Tech stack

| Library | Version |
|---|---|
| Spring Boot BOM | 3.5.11 |
| logback-kafka-appender | 0.2.0-RC2 |
| logstash-logback-encoder | 8.1 |
| Testcontainers | 1.21.4 |
| JaCoCo | 0.8.14 |

---

## License

Apache 2.0 — see [LICENSE](LICENSE).
