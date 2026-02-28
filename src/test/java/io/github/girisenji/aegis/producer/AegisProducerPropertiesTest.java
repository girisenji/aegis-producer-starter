package io.github.girisenji.aegis.producer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AegisProducerProperties} and its nested {@code Kafka} class.
 *
 * <p>Tests setters, getters, and default values directly — without a Spring context —
 * to maximise coverage of the POJO layer.
 *
 * @author Giri Senji
 */
class AegisProducerPropertiesTest {

    // ------------------------------------------------------------------ //
    //  Top-level properties                                                //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("enabled defaults to true via @DefaultValue annotation")
    void enabledDefaultsToTrue() {
        var kafka = new AegisProducerProperties.Kafka("localhost:9092",
                "aegis-production-errors", 0L, "0");
        var props = new AegisProducerProperties(true, "org/repo", kafka);
        assertThat(props.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("setEnabled(false) is reflected in isEnabled()")
    void setEnabledFalse() {
        var kafka = new AegisProducerProperties.Kafka("localhost:9092",
                "aegis-production-errors", 0L, "0");
        var props = new AegisProducerProperties(true, "org/repo", kafka);
        props.setEnabled(false);
        assertThat(props.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("setRepoName updates repoName")
    void setRepoName() {
        var kafka = new AegisProducerProperties.Kafka("", "topic", 0L, "0");
        var props = new AegisProducerProperties(true, "", kafka);
        props.setRepoName("my-org/my-service");
        assertThat(props.getRepoName()).isEqualTo("my-org/my-service");
    }

    @Test
    @DisplayName("getKafka() returns the nested Kafka instance")
    void getKafkaReturnsNestedInstance() {
        var kafka = new AegisProducerProperties.Kafka("broker:9092", "t", 100L, "1");
        var props = new AegisProducerProperties(true, "org/repo", kafka);
        assertThat(props.getKafka()).isSameAs(kafka);
    }

    // ------------------------------------------------------------------ //
    //  Nested Kafka class                                                  //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("Kafka default topic is aegis-production-errors")
    void kafkaDefaultTopic() {
        var kafka = new AegisProducerProperties.Kafka("", "aegis-production-errors", 0L, "0");
        assertThat(kafka.getTopic()).isEqualTo("aegis-production-errors");
    }

    @Test
    @DisplayName("Kafka setBootstrapServers updates bootstrapServers")
    void kafkaSetBootstrapServers() {
        var kafka = new AegisProducerProperties.Kafka("", "topic", 0L, "0");
        kafka.setBootstrapServers("broker1:9092,broker2:9092");
        assertThat(kafka.getBootstrapServers()).isEqualTo("broker1:9092,broker2:9092");
    }

    @Test
    @DisplayName("Kafka setTopic updates topic")
    void kafkaSetTopic() {
        var kafka = new AegisProducerProperties.Kafka("", "original", 0L, "0");
        kafka.setTopic("new-topic");
        assertThat(kafka.getTopic()).isEqualTo("new-topic");
    }

    @Test
    @DisplayName("Kafka setMaxBlockMs updates maxBlockMs")
    void kafkaSetMaxBlockMs() {
        var kafka = new AegisProducerProperties.Kafka("", "topic", 0L, "0");
        kafka.setMaxBlockMs(250L);
        assertThat(kafka.getMaxBlockMs()).isEqualTo(250L);
    }

    @Test
    @DisplayName("Kafka setAcks updates acks")
    void kafkaSetAcks() {
        var kafka = new AegisProducerProperties.Kafka("", "topic", 0L, "0");
        kafka.setAcks("all");
        assertThat(kafka.getAcks()).isEqualTo("all");
    }

    @Test
    @DisplayName("Kafka getMaxBlockMs default is zero (fire-and-forget)")
    void kafkaDefaultMaxBlockMs() {
        var kafka = new AegisProducerProperties.Kafka("", "topic", 0L, "0");
        assertThat(kafka.getMaxBlockMs()).isZero();
    }

    @Test
    @DisplayName("Kafka getAcks default is '0' (no acknowledgement)")
    void kafkaDefaultAcks() {
        var kafka = new AegisProducerProperties.Kafka("", "topic", 0L, "0");
        assertThat(kafka.getAcks()).isEqualTo("0");
    }
}
