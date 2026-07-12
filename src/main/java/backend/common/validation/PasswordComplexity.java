package backend.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a password contains at least one letter and at least one digit,
 * in addition to the @Size constraint applied at the field.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordComplexityValidator.class)
public @interface PasswordComplexity {
    String message() default "must contain at least one letter and one digit";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
