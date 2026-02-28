package io.github.girisenji.aegis.producer;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized configuration for the Aegis producer starter.
 *
 * <p>Minimum required properties in {@code application.yml}:
 * <pre>
 * aegis:
 *   repo-name: my-org/my-service
 *   kafka:
 *     bootstrap-servers: kafka-broker-1:9092,kafka-broker-2:9092
 * </pre>
 *
 * <p>The starter is activated when both {@code aegis.enabled=true} (the default)
 * and the {@code KafkaAppender} class is on the classpath.
 *
 * @author Giri Senji
 */
@ConfigurationProperties(prefix = "aegis")
public final class AegisProducerProperties {

    /**
     * Whether the Aegis producer integration is enabled.
     * Defaults to {@code true}; set to {@code false} to suppress all Aegis wiring
     * (useful in unit/integration test slices that should not reach Kafka).
     */
    private boolean enabled = true;

    /**
     * GitHub repository identifier in the form {@code org/repo-name}.
     * Forwarded as the {@code repoName} JSON field on every error event so the
     * Aegis agent can locate source code without ambiguity.
     */
    private String repoName;

    /**
     * Kafka connection properties used by the logback-kafka-appender.
     */
    private Kafka kafka = new Kafka();

    /** No-arg constructor used by Spring Boot's JavaBean property binding. */
    public AegisProducerProperties() {}

    /** Convenience constructor for programmatic use and testing. */
    public AegisProducerProperties(boolean enabled, String repoName, Kafka kafka) {
        this.enabled = enabled;
        this.repoName = repoName;
        this.kafka = kafka;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getRepoName() {
        return repoName;
    }

    public void setRepoName(String repoName) {
        this.repoName = repoName;
    }

    public Kafka getKafka() {
        return kafka;
    }

    public void setKafka(Kafka kafka) {
        this.kafka = kafka;
    }

    // ------------------------------------------------------------------ //
    //  Nested: Kafka                                                       //
    // ------------------------------------------------------------------ //

    /**
     * Kafka-specific properties forwarded to the logback-kafka-appender's
     * {@code producerConfig} elements.
     */
    public static final class Kafka {

        /**
         * Comma-separated list of {@code host:port} pairs for the Kafka bootstrap
         * servers. Exposed as the {@code KAFKA_BROKERS} logback property.
         * Example: {@code kafka-broker-1:9092,kafka-broker-2:9092}
         */
        private String bootstrapServers = "";

        /**
         * Name of the Kafka topic to which error events are published.
         * Defaults to {@code aegis-production-errors}.
         */
        private String topic = "aegis-production-errors";

        /**
         * How long (ms) the Kafka producer will block waiting for buffer space.
         * Defaults to {@code 0} (never block — drop rather than stall the app).
         */
        private long maxBlockMs = 0L;

        /**
         * Producer acknowledgement mode.
         * Defaults to {@code 0} (fire-and-forget — latency over durability).
         */
        private String acks = "0";

        /** No-arg constructor used by Spring Boot's JavaBean property binding. */
        public Kafka() {}

        /** Convenience constructor for programmatic use and testing. */
        public Kafka(String bootstrapServers, String topic, long maxBlockMs, String acks) {
            this.bootstrapServers = bootstrapServers;
            this.topic = topic;
            this.maxBlockMs = maxBlockMs;
            this.acks = acks;
        }

        public String getBootstrapServers() {
            return bootstrapServers;
        }

        public void setBootstrapServers(String bootstrapServers) {
            this.bootstrapServers = bootstrapServers;
        }

        public String getTopic() {
            return topic;
        }

        public void setTopic(String topic) {
            this.topic = topic;
        }

        public long getMaxBlockMs() {
            return maxBlockMs;
        }

        public void setMaxBlockMs(long maxBlockMs) {
            this.maxBlockMs = maxBlockMs;
        }

        public String getAcks() {
            return acks;
        }

        public void setAcks(String acks) {
            this.acks = acks;
        }
    }
}