package io.github.girisenji.aegis.producer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AegisProducerAutoConfiguration}.
 *
 * <p>Uses {@link ApplicationContextRunner} — the idiomatic Spring Boot approach
 * for testing auto-configurations without starting a full application context.
 * Each scenario creates an isolated context with specific environmental conditions.
 *
 * @author Giri Senji
 */
class AegisProducerAutoConfigurationTest {

    /**
     * Base runner: loads {@link AegisProducerAutoConfiguration} and provides
     * the minimum required properties so the validator bean starts cleanly.
     */
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AegisProducerAutoConfiguration.class))
            .withPropertyValues(
                    "aegis.kafka.bootstrap-servers=localhost:9092",
                    "aegis.repo-name=test-org/test-service"
            );

    // ------------------------------------------------------------------ //
    //  Happy path                                                          //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("When enabled (default)")
    class WhenEnabled {

        @Test
        @DisplayName("auto-configuration bean is registered")
        void autoConfigBeanRegistered() {
            runner.run(ctx ->
                    assertThat(ctx).hasSingleBean(AegisProducerAutoConfiguration.class));
        }

        @Test
        @DisplayName("validator bean is registered")
        void validatorBeanRegistered() {
            runner.run(ctx ->
                    assertThat(ctx).hasSingleBean(
                            AegisProducerAutoConfiguration.AegisProducerPropertiesValidator.class));
        }

        @Test
        @DisplayName("configuration properties bean is registered and bound")
        void propertiesBound() {
            runner.run(ctx -> {
                var props = ctx.getBean(AegisProducerProperties.class);
                assertThat(props.getKafka().getBootstrapServers()).isEqualTo("localhost:9092");
                assertThat(props.getRepoName()).isEqualTo("test-org/test-service");
            });
        }

        @Test
        @DisplayName("properties defaults are applied")
        void propertiesDefaults() {
            runner.run(ctx -> {
                var props = ctx.getBean(AegisProducerProperties.class);
                assertThat(props.isEnabled()).isTrue();
                assertThat(props.getKafka().getTopic()).isEqualTo("aegis-production-errors");
                assertThat(props.getKafka().getMaxBlockMs()).isZero();
                assertThat(props.getKafka().getAcks()).isEqualTo("0");
            });
        }

        @Test
        @DisplayName("custom topic is bound from properties")
        void customTopicBound() {
            runner.withPropertyValues("aegis.kafka.topic=my-custom-topic")
                    .run(ctx -> {
                        var props = ctx.getBean(AegisProducerProperties.class);
                        assertThat(props.getKafka().getTopic()).isEqualTo("my-custom-topic");
                    });
        }

        @Test
        @DisplayName("custom max.block.ms is bound from properties")
        void customMaxBlockMsBound() {
            runner.withPropertyValues("aegis.kafka.max-block-ms=500")
                    .run(ctx -> {
                        var props = ctx.getBean(AegisProducerProperties.class);
                        assertThat(props.getKafka().getMaxBlockMs()).isEqualTo(500L);
                    });
        }
    }

    // ------------------------------------------------------------------ //
    //  Disabled by property                                               //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("When aegis.enabled=false")
    class WhenDisabled {

        @Test
        @DisplayName("auto-configuration bean is NOT registered")
        void autoConfigBeanAbsent() {
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(AegisProducerAutoConfiguration.class))
                    .withPropertyValues("aegis.enabled=false")
                    .run(ctx ->
                            assertThat(ctx).doesNotHaveBean(AegisProducerAutoConfiguration.class));
        }

        @Test
        @DisplayName("validator bean is NOT registered")
        void validatorBeanAbsent() {
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(AegisProducerAutoConfiguration.class))
                    .withPropertyValues("aegis.enabled=false")
                    .run(ctx ->
                            assertThat(ctx).doesNotHaveBean(
                                    AegisProducerAutoConfiguration.AegisProducerPropertiesValidator.class));
        }
    }

    // ------------------------------------------------------------------ //
    //  Validation failures                                                 //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("Validation failures")
    class ValidationFailures {

        @Test
        @DisplayName("context fails when bootstrap-servers is missing")
        void failsWhenBootstrapServersMissing() {
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(AegisProducerAutoConfiguration.class))
                    .withPropertyValues("aegis.repo-name=org/repo")
                    // bootstrap-servers intentionally omitted
                    .run(ctx ->
                            assertThat(ctx).hasFailed()
                                    .getFailure()
                                    .getCause()
                                    .hasMessageContaining("aegis.kafka.bootstrap-servers"));
        }

        @Test
        @DisplayName("context starts with a warning (no failure) when repo-name is missing")
        void startsWithWarningWhenRepoNameMissing() {
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(AegisProducerAutoConfiguration.class))
                    .withPropertyValues("aegis.kafka.bootstrap-servers=localhost:9092")
                    // repo-name intentionally omitted
                    .run(ctx ->
                            // should NOT have failed — missing repo-name is only a warning
                            assertThat(ctx).hasNotFailed());
        }
    }

    // ------------------------------------------------------------------ //
    //  Missing KafkaAppender on classpath                                  //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("Classpath conditions")
    class ClasspathConditions {

        @Test
        @DisplayName("KafkaAppender is on test classpath so auto-config is active")
        void kafkaAppenderIsPresent() {
            // If this test passes, KafkaAppender is on the classpath (required for CI to pass)
            runner.run(ctx ->
                    assertThat(ctx).hasSingleBean(AegisProducerAutoConfiguration.class));
        }
    }
}
