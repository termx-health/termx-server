package com.kodality.commons.util.validation.localdaterange;

import com.kodality.commons.util.range.Range;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class RangeValidators {

  public static class ValidRangeValidator implements ConstraintValidator<ValidRange, Range<?>> {

    @Override
    public boolean isValid(Range<?> value, ConstraintValidatorContext context) {
      return value == null || value.isValid();
    }
  }

  public static class LowerNotNullValidator implements ConstraintValidator<LowerNotNull, Range<?>> {

    @Override
    public boolean isValid(Range<?> value, ConstraintValidatorContext context) {
      return value != null && value.getLower() != null;
    }
  }

  public static class UpperNotNullValidator implements ConstraintValidator<UpperNotNull, Range<?>> {

    @Override
    public boolean isValid(Range<?> value, ConstraintValidatorContext context) {
      return value != null && value.getUpper() != null;
    }
  }
}
