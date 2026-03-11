package com.kodality.commons.util.validation.localdaterange;

import com.kodality.commons.util.validation.localdaterange.RangeValidators.LowerNotNullValidator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({ METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE })
@Retention(RUNTIME)
@Documented
@Constraint(validatedBy = { LowerNotNullValidator.class })
public @interface LowerNotNull {
  String message() default "range lower bound must not be null";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
