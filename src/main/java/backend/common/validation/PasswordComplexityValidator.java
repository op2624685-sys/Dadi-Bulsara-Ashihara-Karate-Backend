package backend.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordComplexityValidator implements ConstraintValidator<PasswordComplexity, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // @NotBlank handles null/empty
        }
        boolean hasLetter = value.chars().anyMatch(Character::isLetter);
        boolean hasDigit = value.chars().anyMatch(Character::isDigit);
        return hasLetter && hasDigit;
    }
}
