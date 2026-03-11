package com.kodality.commons.validation.codename;

import com.kodality.commons.model.CodeName;
import com.kodality.commons.validation.codename.CodeNameValidator.CodeNotNullValidator;
import com.kodality.commons.validation.codename.CodeNameValidator.IdNotNullValidator;
import org.junit.Assert;
import org.junit.Test;

public class CodeNameValidatorTest {

  @Test
  public void testIdValidator() {
    IdNotNullValidator validator = new IdNotNullValidator();
    Assert.assertFalse(validator.isValid(null, null));
    Assert.assertFalse(validator.isValid(new CodeName(), null));
    Assert.assertFalse(validator.isValid(new CodeName("code"), null));
    Assert.assertTrue(validator.isValid(new CodeName(1L), null));
  }

  @Test
  public void testCodeValidator() {
    CodeNotNullValidator validator = new CodeNotNullValidator();
    Assert.assertFalse(validator.isValid(null, null));
    Assert.assertFalse(validator.isValid(new CodeName(), null));
    Assert.assertTrue(validator.isValid(new CodeName("code"), null));
    Assert.assertFalse(validator.isValid(new CodeName(1L), null));
  }

}
