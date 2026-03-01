package io.github.girisenji.aegis.producer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_OFFSET_RESET_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.GROUP_ID_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG;
import static org.assertj.core.api.Assertions.assertThat;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import com.github.danielwegener.logback.kafka.KafkaAppender;
import com.github.danielwegener.logback.kafka.delivery.AsynchronousDeliveryStrategy;
import com.github.danielwegener.logback.kafka.keying.NoKeyKeyingStrategy;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.filter.ThresholdFilter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import net.logstash.logback.composite.loggingevent.LogLevelJsonProvider;
import net.logstash.logback.composite.loggingevent.LoggerNameJsonProvider;
import net.logstash.logback.composite.loggingevent.LoggingEventFormattedTimestampJsonProvider;
import net.logstash.logback.composite.loggingevent.LoggingEventJsonProviders;
import net.logstash.logback.composite.loggingevent.ThreadNameJsonProvider;
import net.logstash.logback.encoder.LoggingEventCompositeJsonEncoder;

/**
 * Integration test: programmatically wires a {@link KafkaAppender} backed by a
 * Testcontainers Kafka broker, fires log events through it, and asserts:
 * <ul>
 *   <li>ERROR events are delivered to the topic.</li>
 *   <li>PII is masked <em>before</em> bytes leave the JVM.</li>
 *   <li>INFO events are dropped by the {@code ThresholdFilter}.</li>
 *   <li>Each delivered message is valid JSON.</li>
 * </ul>
 *
 * <p>Building the appender programmatically (rather than via {@code logback-test.xml})
 * lets us inject the dynamic Testcontainers port without relying on system-property
 * substitution in Logback XML, keeping the test hermetically isolated.
 *
 * <p>Named {@code *IT} so Maven Failsafe runs it during {@code integration-test},
 * separate from the fast unit-test phase.
 *
 * @author Giri Senji
 */
@Testcontainers(disabledWithoutDocker = true)
class LogbackKafkaIntegrationIT {

    private static final String TOPIC = "aegis-production-errors-test";

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("apache/kafka-native:latest"));

    private KafkaAppender<ILoggingEvent>               kafkaAppender;
    private AsyncAppender                               asyncAppender;
    private Logger                                      testLogger;
    private KafkaMessageListenerContainer<String, String> kafkaConsumer;
    private final List<String> received = new ArrayList<>();

    // ------------------------------------------------------------------ //
    //  Set-up / tear-down                                                  //
    // ------------------------------------------------------------------ //

    @BeforeEach
    void setUp() {
        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();

        // ── JSON encoder with PII-masking message provider ─────────────
        var encoder = new LoggingEventCompositeJsonEncoder();
        encoder.setContext(ctx);

        var timestamp = new LoggingEventFormattedTimestampJsonProvider();
        timestamp.setFieldName("@timestamp");
        timestamp.setContext(ctx);

        var levelProvider = new LogLevelJsonProvider();
        levelProvider.setContext(ctx);

        var maskedMsg = new MaskingMessageJsonProvider();
        maskedMsg.setContext(ctx);

        var loggerNameProvider = new LoggerNameJsonProvider();
        loggerNameProvider.setFieldName("logger_name");
        loggerNameProvider.setContext(ctx);

        var threadNameProvider = new ThreadNameJsonProvider();
        threadNameProvider.setFieldName("thread_name");
        threadNameProvider.setContext(ctx);

        LoggingEventJsonProviders providers = (LoggingEventJsonProviders) encoder.getProviders();
        providers.addTimestamp(timestamp);
        providers.addLogLevel(levelProvider);
        providers.addLoggerName(loggerNameProvider);
        providers.addThreadName(threadNameProvider);
        providers.addProvider(maskedMsg);
        encoder.start();

        // ── ThresholdFilter: ERROR and above only ───────────────────────
        var filter = new ThresholdFilter();
        filter.setLevel("ERROR");
        filter.setContext(ctx);
        filter.start();

        // ── KafkaAppender ───────────────────────────────────────────────
        kafkaAppender = new KafkaAppender<>();
        kafkaAppender.setContext(ctx);
        kafkaAppender.setName("KAFKA_IT");
        kafkaAppender.setEncoder(encoder);
        kafkaAppender.setTopic(TOPIC);
        kafkaAppender.setKeyingStrategy(new NoKeyKeyingStrategy());
        kafkaAppender.setDeliveryStrategy(new AsynchronousDeliveryStrategy());
        kafkaAppender.addProducerConfig("bootstrap.servers=" + KAFKA.getBootstrapServers());
        kafkaAppender.addProducerConfig("max.block.ms=5000");  // relaxed for test reliability
        kafkaAppender.addProducerConfig("acks=1");
        kafkaAppender.addProducerConfig("retries=3");
        kafkaAppender.addFilter(filter);
        kafkaAppender.start();

        // ── AsyncAppender (neverBlock=false so delivery is deterministic) ─
        asyncAppender = new AsyncAppender();
        asyncAppender.setContext(ctx);
        asyncAppender.setName("ASYNC_KAFKA_IT");
        asyncAppender.addAppender(kafkaAppender);
        asyncAppender.setQueueSize(512);
        asyncAppender.setNeverBlock(false);
        asyncAppender.start();

        // ── Dedicated test logger (no propagation to root) ──────────────
        testLogger = ctx.getLogger("aegis.it." + getClass().getSimpleName());
        testLogger.setLevel(Level.DEBUG);
        testLogger.addAppender(asyncAppender);
        testLogger.setAdditive(false);

        // ── Spring-Kafka consumer to inspect what the appender sends ─────
        Map<String, Object> consumerProps = Map.of(
                BOOTSTRAP_SERVERS_CONFIG,        KAFKA.getBootstrapServers(),
                GROUP_ID_CONFIG,                 "aegis-it-" + System.nanoTime(),
                KEY_DESERIALIZER_CLASS_CONFIG,   "org.apache.kafka.common.serialization.StringDeserializer",
                VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer",
                AUTO_OFFSET_RESET_CONFIG,        "earliest"
        );
        var containerProps = new ContainerProperties(TOPIC);
        containerProps.setMessageListener(
                (MessageListener<String, String>) record -> received.add(record.value()));
        kafkaConsumer = new KafkaMessageListenerContainer<>(
                new DefaultKafkaConsumerFactory<>(consumerProps), containerProps);
        kafkaConsumer.start();
    }

    @AfterEach
    void tearDown() {
        if (kafkaConsumer != null) kafkaConsumer.stop();
        if (asyncAppender  != null) asyncAppender.stop();
        if (kafkaAppender  != null) kafkaAppender.stop();
        if (testLogger     != null) testLogger.detachAndStopAllAppenders();
        received.clear();
    }

    // ------------------------------------------------------------------ //
    //  Tests                                                               //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("ERROR log event arrives in Kafka topic")
    void errorEventArrivesInKafka() {
        testLogger.error("Integration test error — pipeline alive");

        awaitMessages(1);
        assertThat(received).isNotEmpty();
    }

    @Test
    @DisplayName("PII is masked before reaching Kafka")
    void piiIsMaskedBeforeKafka() {
        testLogger.error("Payment failed for user@secret.com with card 4111111111111111");

        awaitMessages(1);
        String payload = String.join(" ", received);
        assertThat(payload)
                .doesNotContain("user@secret.com")
                .doesNotContain("4111111111111111")
                .contains("[EMAIL]")
                .contains("[CARD]");
    }

    @Test
    @DisplayName("INFO event is NOT forwarded to Kafka (ThresholdFilter: ERROR only)")
    void infoEventNotForwardedToKafka() throws InterruptedException {
        testLogger.error("Sentinel error");
        awaitMessages(1);
        int countAfterError = received.size();

        testLogger.info("This info must never reach Kafka");
        Thread.sleep(1_000);

        assertThat(received).hasSize(countAfterError);
    }

    @Test
    @DisplayName("delivered message is valid JSON with expected fields")
    void messageIsValidJson() {
        testLogger.error("Checking JSON structure");

        awaitMessages(1);
        String payload = received.getFirst();
        assertThat(payload)
                .startsWith("{")
                .endsWith("}")
                .contains("\"level\"")
                .contains("\"message\"")
                .contains("\"@timestamp\"")
                // Field names must match @JsonProperty in ErrorLogEvent
                .contains("\"logger_name\"")
                .contains("\"thread_name\"");
    }

    @Test
    @DisplayName("Bearer token is masked in the Kafka message")
    void bearerTokenMasked() {
        testLogger.error("Auth failed: Bearer eyJhbGciOiJSUzI1NiJ9.payload.sig");

        awaitMessages(1);
        assertThat(String.join(" ", received))
                .contains("[TOKEN]")
                .doesNotContain("eyJhbGciOiJSUzI1NiJ9");
    }

    // ------------------------------------------------------------------ //
    //  Helpers                                                             //
    // ------------------------------------------------------------------ //

    private void awaitMessages(int atLeast) {
        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> received.size() >= atLeast);
    }
}
