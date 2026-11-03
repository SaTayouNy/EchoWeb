package org.example.echo.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/** BCrypt consumes at most 72 bytes, which is not the same as 72 characters. */
@Target({FIELD, PARAMETER, RECORD_COMPONENT, METHOD, ANNOTATION_TYPE})
@Retention(RUNTIME)
@Constraint(validatedBy = Utf8SizeValidator.class)
public @interface Utf8Size {
    String message() default "must contain at most {max} UTF-8 bytes";
    int max() default 72;
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
