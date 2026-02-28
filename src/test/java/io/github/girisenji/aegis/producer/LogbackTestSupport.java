package io.github.girisenji.aegis.producer;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared test utilities for constructing Logback objects in unit tests.
 *
 * @author Giri Senji
 */
final class LogbackTestSupport {

    private static final Logger TEST_LOGGER =
            (Logger) LoggerFactory.getLogger(LogbackTestSupport.class);

    private LogbackTestSupport() {}

    /**
     * Creates a minimal {@link ILoggingEvent} backed by a {@link LoggingEvent}
     * with the given pre-formatted message.
     *
     * @param message the already-formatted message text
     * @return a logging event at ERROR level carrying {@code message}
     */
    static ILoggingEvent eventWithMessage(String message) {
        LoggingEvent event = new LoggingEvent();
        event.setLoggerName(LogbackTestSupport.class.getName());
        event.setLevel(Level.ERROR);
        event.setMessage(message);
        event.setLoggerContext(TEST_LOGGER.getLoggerContext());
        return event;
    }
}
