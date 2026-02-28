package io.github.girisenji.aegis.producer;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.core.JsonGenerator;
import net.logstash.logback.composite.loggingevent.MessageJsonProvider;

import java.io.IOException;

/**
 * Logstash-logback-encoder JSON provider that applies PII masking to the
 * {@code message} field before it is written to the JSON output.
 *
 * <p>Registered as the {@code <message>} provider inside a
 * {@code LoggingEventCompositeJsonEncoder} in {@code logback-spring-aegis.xml},
 * replacing the default {@link MessageJsonProvider}.
 *
 * @author Giri Senji
 * @see PiiMaskingConverter#mask(String)
 */
public final class MaskingMessageJsonProvider extends MessageJsonProvider {

    /**
     * Writes the {@code message} JSON field, with PII redacted before output.
     *
     * @param generator the JSON generator supplied by the encoder pipeline
     * @param event     the logging event being serialised
     */
    @Override
    public void writeTo(JsonGenerator generator, ILoggingEvent event) throws IOException {
        String masked = PiiMaskingConverter.mask(event.getFormattedMessage());
        generator.writeStringField(getFieldName(), masked != null ? masked : "");
    }
}
