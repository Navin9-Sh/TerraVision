package ai.terravision.emailcheck;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class RealEmailValidator implements ConstraintValidator<RealEmail, String> {

    private final EmailDomainResolver resolver;

    public RealEmailValidator(EmailDomainResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public boolean isValid(String email, ConstraintValidatorContext context) {
        if (email == null || email.isBlank()) {
            return true;
        }
        String trimmed = email.trim();
        if (!EmailSyntax.isValid(trimmed)) {
            return false;
        }
        if (!resolver.isDnsCheckEnabled()) {
            return true;
        }

        EmailDomainResolver.Result result = resolver.check(EmailSyntax.domainOf(trimmed));
        return switch (result) {
            case DELIVERABLE, UNKNOWN -> true;
            case NO_SUCH_DOMAIN, NO_MAIL_SERVER -> fail(context,
                    "That email domain can't receive mail. Check for a typo (for example gmail.com).");
        };
    }

    private boolean fail(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message.replace("{", "\\{").replace("}", "\\}"))
                .addConstraintViolation();
        return false;
    }
}
