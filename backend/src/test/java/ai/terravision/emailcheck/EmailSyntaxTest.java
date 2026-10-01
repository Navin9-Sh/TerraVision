package ai.terravision.emailcheck;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class EmailSyntaxTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "navin@gmail.com",
            "first.last@example.co.in",
            "user+tag@sub.domain.org",
            "navin.232673101@vcet.edu.in",
            "a_b-c@my-domain.io",
            "x@xn--80ak6aa92e.com"
    })
    void acceptsWellFormedAddresses(String email) {
        assertThat(EmailSyntax.isValid(email)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "navin@gmail",          // no TLD
            "hi@how",               // no TLD
            "navin@",               // empty domain
            "@gmail.com",           // empty local part
            "navin@@gmail.com",     // two @
            "na vin@gmail.com",     // space
            "navin@gmail..com",     // empty label
            "navin@.gmail.com",     // leading dot
            "navin@gmail.com.",     // trailing dot
            ".navin@gmail.com",     // leading dot in local part
            "na..vin@gmail.com",    // double dot in local part
            "navin@gmail.c",        // one-letter TLD
            "navin@gmail.123",      // numeric TLD
            "navin@-gmail.com",     // label starts with hyphen
            "navin@gmail-.com",     // label ends with hyphen
            "navin@127.0.0.1",      // IP, numeric TLD
            "navin"                 // no @ at all
    })
    void rejectsMalformedAddresses(String email) {
        assertThat(EmailSyntax.isValid(email)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    void rejectsNullAndEmpty(String email) {
        assertThat(EmailSyntax.isValid(email)).isFalse();
    }

    @org.junit.jupiter.api.Test
    void rejectsOverlongAddresses() {
        assertThat(EmailSyntax.isValid("a".repeat(65) + "@example.com")).isFalse();
        assertThat(EmailSyntax.isValid("a@" + "b".repeat(250) + ".com")).isFalse();
    }

    @org.junit.jupiter.api.Test
    void extractsTheDomainInLowerCase() {
        assertThat(EmailSyntax.domainOf("Navin@GMAIL.com")).isEqualTo("gmail.com");
    }
}
