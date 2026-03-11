package com.kodality.commons.util.range;

import com.kodality.commons.util.validation.localdaterange.RangeValidators.LowerNotNullValidator;
import com.kodality.commons.util.validation.localdaterange.RangeValidators.UpperNotNullValidator;
import com.kodality.commons.util.validation.localdaterange.RangeValidators.ValidRangeValidator;
import org.junit.Assert;
import org.junit.Test;

import java.time.LocalDate;

public class RangeValidatorTest {

  @Test
  public void testValidRange() {
    ValidRangeValidator validator = new ValidRangeValidator();
    Assert.assertTrue(validator.isValid(null, null));
    Assert.assertTrue(validator.isValid(new LocalDateRange(null, null), null));
    Assert.assertTrue(validator.isValid(new LocalDateRange(LocalDate.now(), null), null));
    Assert.assertTrue(validator.isValid(new LocalDateRange(null, LocalDate.now()), null));

    Assert.assertTrue(validator.isValid(new LocalDateRange(LocalDate.now(), LocalDate.now()), null));
    Assert.assertTrue(validator.isValid(new LocalDateRange(LocalDate.now().minusDays(1), LocalDate.now()), null));
    Assert.assertFalse(validator.isValid(new LocalDateRange(LocalDate.now().plusDays(1), LocalDate.now()), null));
  }

  @Test
  public void testLowerNotNull() {
    LowerNotNullValidator validator = new LowerNotNullValidator();
    Assert.assertFalse(validator.isValid(null, null));
    Assert.assertFalse(validator.isValid(new LocalDateRange(null, null), null));
    Assert.assertTrue(validator.isValid(new LocalDateRange(LocalDate.now(), null), null));
    Assert.assertFalse(validator.isValid(new LocalDateRange(null, LocalDate.now()), null));
    Assert.assertTrue(validator.isValid(new LocalDateRange(LocalDate.now(), LocalDate.now()), null));
  }

  @Test
  public void testUpperNotNull() {
    UpperNotNullValidator validator = new UpperNotNullValidator();
    Assert.assertFalse(validator.isValid(null, null));
    Assert.assertFalse(validator.isValid(new LocalDateRange(null, null), null));
    Assert.assertFalse(validator.isValid(new LocalDateRange(LocalDate.now(), null), null));
    Assert.assertTrue(validator.isValid(new LocalDateRange(null, LocalDate.now()), null));
    Assert.assertTrue(validator.isValid(new LocalDateRange(LocalDate.now(), LocalDate.now()), null));
  }

}
