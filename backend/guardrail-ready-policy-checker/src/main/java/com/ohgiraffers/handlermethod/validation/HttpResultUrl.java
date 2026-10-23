package com.ohgiraffers.handlermethod.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = HttpResultUrlValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface HttpResultUrl {
    String message() default "Use an HTTP(S) URL with a host and no user information or control characters";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
