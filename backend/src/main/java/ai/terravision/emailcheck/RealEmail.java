package ai.terravision.emailcheck;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** The value must be name@domain.tld at a domain that can actually receive email. */
@Documented
@Constraint(validatedBy = RealEmailValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface RealEmail {

    String message() default "Enter a valid email address, like name@example.com";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
