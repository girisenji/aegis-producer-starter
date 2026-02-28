package io.github.girisenji.aegis.producer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PiiMaskingConverter}.
 *
 * <p>Verifies each PII category independently, combined cases, edge cases,
 * and the static {@code mask()} entry-point used by the JSON provider.
 *
 * @author Giri Senji
 */
class PiiMaskingConverterTest {

    // ------------------------------------------------------------------ //
    //  EMAIL masking                                                       //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("EMAIL pattern")
    class EmailMasking {

        @Test
        @DisplayName("masks a plain email address")
        void masksPlainEmail() {
            assertThat(PiiMaskingConverter.mask("User email: user@example.com failed"))
                    .isEqualTo("User email: [EMAIL] failed");
        }

        @Test
        @DisplayName("masks multiple email addresses in one message")
        void masksMultipleEmails() {
            String result = PiiMaskingConverter.mask("from: a@b.io to: c@d.example.org");
            assertThat(result).doesNotContain("@")
                    .contains("[EMAIL]");
        }

        @Test
        @DisplayName("masks email with plus sign and subdomain")
        void masksEmailWithPlus() {
            assertThat(PiiMaskingConverter.mask("Contact: user+tag@mail.example.co.uk"))
                    .contains("[EMAIL]")
                    .doesNotContain("user+tag@mail.example.co.uk");
        }

        @Test
        @DisplayName("masks email embedded in a stack trace line")
        void masksEmailInStackTrace() {
            String input = "Exception: user=admin@corp.internal, details=...";
            assertThat(PiiMaskingConverter.mask(input))
                    .contains("[EMAIL]")
                    .doesNotContain("admin@corp.internal");
        }

        @Test
        @DisplayName("safe text without email is unchanged")
        void safeTextUnchanged() {
            String safe = "Order 12345 processed successfully";
            assertThat(PiiMaskingConverter.mask(safe)).isEqualTo(safe);
        }
    }

    // ------------------------------------------------------------------ //
    //  TOKEN masking                                                       //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("TOKEN pattern")
    class TokenMasking {

        @Test
        @DisplayName("masks Authorization Bearer header value")
        void masksBearerToken() {
            assertThat(PiiMaskingConverter.mask("Authorization: Bearer eyJhbGciOiJSUzI1NiJ9.payload.sig"))
                    .contains("[TOKEN]")
                    .doesNotContain("eyJhbGciOiJSUzI1NiJ9");
        }

        @Test
        @DisplayName("masks token= assignment (case-insensitive)")
        void masksTokenAssignment() {
            assertThat(PiiMaskingConverter.mask("Retry-After token=secret_value_xyz"))
                    .contains("[TOKEN]")
                    .doesNotContain("secret_value_xyz");
        }

        @Test
        @DisplayName("masks token: colon-separated value")
        void masksTokenColon() {
            assertThat(PiiMaskingConverter.mask("debug token: ghp_abc123DEF456"))
                    .contains("[TOKEN]")
                    .doesNotContain("ghp_abc123DEF456");
        }

        @Test
        @DisplayName("masks mixed-case Bearer keyword")
        void masksMixedCaseBearer() {
            assertThat(PiiMaskingConverter.mask("header BEARER myAccessToken123"))
                    .contains("[TOKEN]")
                    .doesNotContain("myAccessToken123");
        }

        @Test
        @DisplayName("masks Authorization keyword")
        void masksAuthorizationKeyword() {
            assertThat(PiiMaskingConverter.mask("Authorization=Basic dXNlcjpwYXNz"))
                    .contains("[TOKEN]")
                    .doesNotContain("dXNlcjpwYXNz");
        }
    }

    // ------------------------------------------------------------------ //
    //  CARD masking                                                        //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("CARD pattern")
    class CardMasking {

        @Test
        @DisplayName("masks a 16-digit card number (no separators)")
        void masks16DigitCard() {
            assertThat(PiiMaskingConverter.mask("Card: 4111111111111111 declined"))
                    .contains("[CARD]")
                    .doesNotContain("4111111111111111");
        }

        @Test
        @DisplayName("masks a 16-digit card with dash separators")
        void masks16DigitCardDashes() {
            assertThat(PiiMaskingConverter.mask("Charge on 4111-1111-1111-1111"))
                    .contains("[CARD]")
                    .doesNotContain("4111-1111-1111-1111");
        }

        @Test
        @DisplayName("masks a 16-digit card with space separators")
        void masks16DigitCardSpaces() {
            assertThat(PiiMaskingConverter.mask("Payment 4111 1111 1111 1111 refused"))
                    .contains("[CARD]")
                    .doesNotContain("4111 1111 1111 1111");
        }

        @Test
        @DisplayName("masks a 13-digit card number")
        void masks13DigitCard() {
            assertThat(PiiMaskingConverter.mask("Card: 4111111111111 auth failed"))
                    .contains("[CARD]")
                    .doesNotContain("4111111111111");
        }

        @Test
        @DisplayName("short number with fewer than 13 digits is NOT masked")
        void shortNumberNotMasked() {
            String input = "Order ID: 123456789012";   // 12 digits — not a card
            assertThat(PiiMaskingConverter.mask(input)).isEqualTo(input);
        }
    }

    // ------------------------------------------------------------------ //
    //  Combined and edge cases                                             //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("Combined and edge cases")
    class CombinedAndEdgeCases {

        @Test
        @DisplayName("masks all three PII types in a single message")
        void masksAllThreeTypes() {
            String input = "user@corp.com paid 4111111111111111 via Bearer mytoken123";
            String result = PiiMaskingConverter.mask(input);
            assertThat(result)
                    .contains("[EMAIL]")
                    .contains("[CARD]")
                    .contains("[TOKEN]")
                    .doesNotContain("user@corp.com")
                    .doesNotContain("4111111111111111")
                    .doesNotContain("mytoken123");
        }

        @Test
        @DisplayName("null input returns null")
        void nullInputReturnsNull() {
            assertThat(PiiMaskingConverter.mask(null)).isNull();
        }

        @ParameterizedTest(name = "empty/blank input [{0}] is returned as-is")
        @NullAndEmptySource
        @ValueSource(strings = {" ", "   "})
        void emptyOrBlankReturnsInput(String input) {
            // null is tested separately; for others we check the string comes back unchanged
            if (input != null) {
                assertThat(PiiMaskingConverter.mask(input)).isEqualTo(input);
            }
        }

        @Test
        @DisplayName("message with no PII is returned unchanged")
        void noPiiUnchanged() {
            String safe = "com.example.service.PaymentService threw NullPointerException at line 42";
            assertThat(PiiMaskingConverter.mask(safe)).isEqualTo(safe);
        }

        @Test
        @DisplayName("very long message is handled without error")
        void longMessageHandled() {
            String longMsg = "x".repeat(10_000) + " user@example.com " + "y".repeat(10_000);
            String result = PiiMaskingConverter.mask(longMsg);
            assertThat(result).contains("[EMAIL]").doesNotContain("user@example.com");
        }

        @Test
        @DisplayName("repeated masking is idempotent")
        void maskingIsIdempotent() {
            String once  = PiiMaskingConverter.mask("error for user@example.com");
            String twice = PiiMaskingConverter.mask(once);
            assertThat(once).isEqualTo(twice);
        }
    }

    // ------------------------------------------------------------------ //
    //  ILoggingEvent path via convert()                                    //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("convert(ILoggingEvent) — Logback converter path")
    class ConvertMethod {

        @Test
        @DisplayName("convert delegates to mask() correctly")
        void convertDelegatesToMask() {
            var converter = new PiiMaskingConverter();
            var event = LogbackTestSupport.eventWithMessage("User: admin@example.com");
            assertThat(converter.convert(event))
                    .contains("[EMAIL]")
                    .doesNotContain("admin@example.com");
        }

        @Test
        @DisplayName("convert returns masked result for TOKEN")
        void convertMasksToken() {
            var converter = new PiiMaskingConverter();
            var event = LogbackTestSupport.eventWithMessage("call with Bearer secret999 failed");
            assertThat(converter.convert(event))
                    .contains("[TOKEN]")
                    .doesNotContain("secret999");
        }
    }
}
