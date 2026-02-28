package io.github.girisenji.aegis.producer;

import com.github.danielwegener.logback.kafka.KafkaAppender;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

/**
 * Spring Boot auto-configuration for the Aegis producer starter.
 *
 * <h3>Activation conditions</h3>
 * <ol>
 *   <li>{@link KafkaAppender} must be on the classpath (i.e. the consumer has declared the
 *       {@code logback-kafka-appender} dependency).</li>
 *   <li>{@code aegis.enabled=true} (the default; set to {@code false} to suppress all wiring,
 *       e.g. in test slices that should not contact Kafka).</li>
 * </ol>
 *
 * <h3>What this auto-configuration does</h3>
 * <ul>
 *   <li>Binds {@link AegisProducerProperties} to the {@code aegis.*} namespace.</li>
 *   <li>Registers a {@link AegisProducerPropertiesValidator} bean that emits a clear,
 *       actionable error at startup when required properties are missing, rather than
 *       letting the application silently drop error events.</li>
 * </ul>
 *
 * <p><strong>Logback wiring</strong> — the {@link PiiMaskingConverter} and
 * {@link MaskingMessageJsonProvider} operate entirely within the Logback pipeline and
 * require no Spring bean registration. They are activated by including
 * {@code logback-spring-aegis.xml} in the consumer's {@code logback-spring.xml}:
 * <pre>{@code
 * <include resource="logback-spring-aegis.xml"/>
 * }</pre>
 *
 * @author Giri Senji
 */
@AutoConfiguration
@ConditionalOnClass(KafkaAppender.class)
@ConditionalOnProperty(prefix = "aegis", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(AegisProducerProperties.class)
public class AegisProducerAutoConfiguration {

    /**
     * Registers a validator bean that performs eager startup checks on the
     * bound {@link AegisProducerProperties}.
     *
     * @param properties the bound configuration properties
     * @return the validator bean
     */
    @Bean
    public AegisProducerPropertiesValidator aegisProducerPropertiesValidator(
            AegisProducerProperties properties) {
        return new AegisProducerPropertiesValidator(properties);
    }

    // ------------------------------------------------------------------ //
    //  Inner validator                                                     //
    // ------------------------------------------------------------------ //

    /**
     * Performs fail-fast validation of required Aegis producer properties.
     *
     * <p>Logs a detailed warning (rather than throwing) when optional-but-recommended
     * properties are absent, and throws {@link IllegalStateException} when mandatory
     * properties are missing.
     */
    public static final class AegisProducerPropertiesValidator {

        private static final Logger log =
                LoggerFactory.getLogger(AegisProducerPropertiesValidator.class);

        private final AegisProducerProperties properties;

        AegisProducerPropertiesValidator(AegisProducerProperties properties) {
            this.properties = properties;
        }

        @PostConstruct
        void validate() {
            AegisProducerProperties.Kafka kafka = properties.getKafka();

            if (!StringUtils.hasText(kafka.getBootstrapServers())) {
                throw new IllegalStateException(
                        "[Aegis] aegis.kafka.bootstrap-servers must be set when aegis.enabled=true. "
                        + "Add 'aegis.kafka.bootstrap-servers: <host>:<port>' to your application.yml.");
            }

            if (!StringUtils.hasText(properties.getRepoName())) {
                log.warn("[Aegis] aegis.repo-name is not set. The Aegis agent will be unable to "
                        + "locate source files for automated PR creation. "
                        + "Set 'aegis.repo-name: <org>/<repo>' in application.yml.");
            }

            log.info("[Aegis] Producer starter active — topic={}, brokers={}, repo={}",
                    kafka.getTopic(),
                    kafka.getBootstrapServers(),
                    properties.getRepoName());
        }
    }
}
