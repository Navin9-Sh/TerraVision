package ai.terravision.emailcheck;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RealEmailValidatorTest {

    private final EmailDomainResolver resolver = mock(EmailDomainResolver.class);
    private final RealEmailValidator validator = new RealEmailValidator(resolver);
    private final ConstraintValidatorContext context = mock(ConstraintValidatorContext.class, Mockito.RETURNS_DEEP_STUBS);

    private void dnsEnabled(boolean enabled) {
        when(resolver.isDnsCheckEnabled()).thenReturn(enabled);
    }

    @Test
    void blankIsLeftToNotBlank() {
        assertThat(validator.isValid(null, context)).isTrue();
        assertThat(validator.isValid("  ", context)).isTrue();
    }

    @Test
    void badSyntaxFailsWithoutTouchingDns() {
        dnsEnabled(true);
        assertThat(validator.isValid("navin@gmail", context)).isFalse();
        assertThat(validator.isValid("hi@how", context)).isFalse();
        verify(resolver, never()).check(anyString());
    }

    @Test
    void domainThatCanReceiveMailIsAccepted() {
        dnsEnabled(true);
        when(resolver.check("gmail.com")).thenReturn(EmailDomainResolver.Result.DELIVERABLE);
        assertThat(validator.isValid("Navin@Gmail.com", context)).isTrue();
    }

    @Test
    void nonexistentDomainIsRejected() {
        dnsEnabled(true);
        when(resolver.check("gmial-typo-example.com")).thenReturn(EmailDomainResolver.Result.NO_SUCH_DOMAIN);
        assertThat(validator.isValid("navin@gmial-typo-example.com", context)).isFalse();
    }

    @Test
    void domainWithoutAMailServerIsRejected() {
        dnsEnabled(true);
        when(resolver.check("no-mail.example")).thenReturn(EmailDomainResolver.Result.NO_MAIL_SERVER);
        assertThat(validator.isValid("a@no-mail.example", context)).isFalse();
    }

    @Test
    void dnsOutageDoesNotBlockSignup() {
        dnsEnabled(true);
        when(resolver.check("example.org")).thenReturn(EmailDomainResolver.Result.UNKNOWN);
        assertThat(validator.isValid("a@example.org", context)).isTrue();
    }

    @Test
    void dnsCheckCanBeSwitchedOff() {
        dnsEnabled(false);
        assertThat(validator.isValid("a@anything.test", context)).isTrue();
        verify(resolver, never()).check(anyString());
    }
}
