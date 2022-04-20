package com.kodality.termserver.commons.model.model.validation;

import com.kodality.termserver.commons.model.model.CodeNameable;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

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
