package io.github.girisenji.aegis.producer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Logback {@link MessageConverter} that scrubs sensitive data from log messages
 * before they are serialised and forwarded to Kafka.
 *
 * <h3>Patterns masked</h3>
 * <ul>
 *   <li>{@code EMAIL} — any RFC-5321-style address → {@code [EMAIL]}</li>
 *   <li>{@code TOKEN} — {@code Bearer}, {@code token}, or {@code Authorization} values → {@code [TOKEN]}</li>
 *   <li>{@code CARD}  — 13–16 contiguous or space/dash-separated digits → {@code [CARD]}</li>
 * </ul>
 *
 * <p>Used in two ways:
 * <ol>
 *   <li>As a Logback {@code <conversionRule>} in XML configuration (inherits
 *       {@link MessageConverter}), so pattern-layout appenders can reference it
 *       via {@code %maskedMsg}.</li>
 *   <li>Via the static {@link #mask(String)} helper, consumed by
 *       {@link MaskingMessageJsonProvider} to protect the JSON-encoded message
 *       field written by logstash-logback-encoder.</li>
 * </ol>
 *
 * <p>Thread-safe: all state is stored in {@code static final} compiled patterns.
 *
 * @author Giri Senji
 */
public final class PiiMaskingConverter extends MessageConverter {

    // ------------------------------------------------------------------ //
    //  Compiled patterns — package-private for test access                //
    // ------------------------------------------------------------------ //

    static final Pattern EMAIL = Pattern.compile(
            "[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}");

    static final Pattern TOKEN = Pattern.compile(
            "(?i)(?:Authorization[\\s:=]+(?:(?:Bearer|Basic|Digest)[\\s]+)?|(?:Bearer|token)[\\s:=]+)[^\\s,;\"']+");

    static final Pattern CARD = Pattern.compile(
            "\\b(?:\\d[ \\-]?){12,15}\\d\\b");

    // ------------------------------------------------------------------ //
    //  Replacement tokens                                                  //
    // ------------------------------------------------------------------ //

    static final String EMAIL_REPLACEMENT = "[EMAIL]";
    static final String TOKEN_REPLACEMENT = "[TOKEN]";
    static final String CARD_REPLACEMENT  = "[CARD]";

    /**
     * Called by Logback when this converter is used in a pattern layout
     * (e.g. {@code %maskedMsg}).
     *
     * @param event the logging event whose formatted message will be masked
     * @return the masked message string
     */
    @Override
    public String convert(ILoggingEvent event) {
        return mask(event.getFormattedMessage());
    }

    /**
     * Pure-function static entry-point used by {@link MaskingMessageJsonProvider}
     * and unit tests.
     *
     * <p>Applies masking in order: EMAIL → TOKEN → CARD. The relative order
     * matters because {@code TOKEN} patterns can contain email-like strings.
     *
     * @param message raw log message text; may be {@code null}
     * @return masked text, or {@code null} when the input is {@code null}
     */
    public static String mask(String message) {
        if (message == null) {
            return null;
        }

        String result = message;
        result = replaceAll(EMAIL, result, EMAIL_REPLACEMENT);
        result = replaceAll(TOKEN, result, TOKEN_REPLACEMENT);
        result = replaceAll(CARD,  result, CARD_REPLACEMENT);
        return result;
    }

    // ------------------------------------------------------------------ //
    //  Private helpers                                                     //
    // ------------------------------------------------------------------ //

    /**
     * Resets and reuses a {@link Matcher} for the given pattern, replacing
     * every match with {@code replacement}.
     */
    private static String replaceAll(Pattern pattern, String input, String replacement) {
        Matcher matcher = pattern.matcher(input);
        if (!matcher.find()) {
            return input; // fast-path: no match, avoid StringBuilder allocation
        }
        matcher.reset();
        return matcher.replaceAll(Matcher.quoteReplacement(replacement));
    }
}
