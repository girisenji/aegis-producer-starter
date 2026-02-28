package io.github.girisenji.aegis.producer;

import com.fasterxml.jackson.core.io.JsonStringEncoder;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.logstash.logback.composite.loggingevent.MessageJsonProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Unit tests for {@link MaskingMessageJsonProvider}.
 *
 * <p>Tests the JSON serialisation path: verifies the provider writes a
 * properly quoted, PII-masked {@code message} field to the JSON output.
 *
 * @author Giri Senji
 */
class MaskingMessageJsonProviderTest {

    private final MaskingMessageJsonProvider provider = new MaskingMessageJsonProvider();

    @Test
    @DisplayName("inherits MessageJsonProvider — correct type hierarchy")
    void inheritsMessageJsonProvider() {
        assertThat(provider).isInstanceOf(MessageJsonProvider.class);
    }

    @Test
    @DisplayName("getFieldName() returns 'message' by default")
    void defaultFieldName() {
        assertThat(provider.getFieldName()).isEqualTo("message");
    }

    @Test
    @DisplayName("writeTo masks an email address in the message field")
    void writeToMasksEmail() throws Exception {
        var event = LogbackTestSupport.eventWithMessage("contact admin@example.com now");

        var os = new ByteArrayOutputStream();
        try (var gen = new ObjectMapper().createGenerator(os)) {
            gen.writeStartObject();
            provider.writeTo(gen, event);
            gen.writeEndObject();
        }

        String json = os.toString();
        assertThat(json).contains("\"message\"")
                .contains("[EMAIL]")
                .doesNotContain("admin@example.com");
    }

    @Test
    @DisplayName("writeTo masks a Bearer token in the message field")
    void writeToMasksToken() throws Exception {
        var event = LogbackTestSupport.eventWithMessage("failed with Bearer topsecret123");

        var os = new ByteArrayOutputStream();
        try (var gen = new ObjectMapper().createGenerator(os)) {
            gen.writeStartObject();
            provider.writeTo(gen, event);
            gen.writeEndObject();
        }

        String json = os.toString();
        assertThat(json).contains("[TOKEN]").doesNotContain("topsecret123");
    }

    @Test
    @DisplayName("writeTo masks a card number in the message field")
    void writeToMasksCard() throws Exception {
        var event = LogbackTestSupport.eventWithMessage("card 4111111111111111 declined");

        var os = new ByteArrayOutputStream();
        try (var gen = new ObjectMapper().createGenerator(os)) {
            gen.writeStartObject();
            provider.writeTo(gen, event);
            gen.writeEndObject();
        }

        String json = os.toString();
        assertThat(json).contains("[CARD]").doesNotContain("4111111111111111");
    }

    @Test
    @DisplayName("writeTo handles null formatted message without throwing")
    void writeToHandlesNullMessage() throws Exception {
        var event = LogbackTestSupport.eventWithMessage(null);

        var os = new ByteArrayOutputStream();
        try (var gen = new ObjectMapper().createGenerator(os)) {
            gen.writeStartObject();
            assertThatNoException().isThrownBy(() -> provider.writeTo(gen, event));
            gen.writeEndObject();
        }
    }

    @Test
    @DisplayName("writeTo produces valid JSON output")
    void writeToProducesValidJson() throws Exception {
        var event = LogbackTestSupport.eventWithMessage("simple safe message");

        var os = new ByteArrayOutputStream();
        try (var gen = new ObjectMapper().createGenerator(os)) {
            gen.writeStartObject();
            provider.writeTo(gen, event);
            gen.writeEndObject();
        }

        // Parse the JSON to verify it is structurally valid
        var mapper = new ObjectMapper();
        assertThatNoException()
                .isThrownBy(() -> mapper.readTree(os.toString()));
    }
}
