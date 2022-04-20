package com.kodality.termserver.commons.util.range.validation;

import com.kodality.termserver.commons.util.range.Range;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

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
