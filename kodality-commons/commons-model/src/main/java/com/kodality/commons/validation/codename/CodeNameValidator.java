package com.kodality.commons.validation.codename;

import com.kodality.commons.model.CodeNameable;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CodeNameValidator {

  public static class IdNotNullValidator implements ConstraintValidator<IdNotNull, CodeNameable> {

    @Override
    public boolean isValid(CodeNameable codeName, ConstraintValidatorContext constraintValidatorContext) {
      return codeName != null && codeName.getId() != null;
    }
  }

  public static class CodeNotNullValidator implements ConstraintValidator<CodeNotNull, CodeNameable> {

    @Override
    public boolean isValid(CodeNameable codeName, ConstraintValidatorContext constraintValidatorContext) {
      return codeName != null && codeName.getCode() != null;
    }
  }

}
