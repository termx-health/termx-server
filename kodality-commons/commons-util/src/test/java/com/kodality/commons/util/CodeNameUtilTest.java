package com.kodality.commons.util;

import com.kodality.commons.model.CodeName;
import org.junit.Assert;
import org.junit.Test;

public class CodeNameUtilTest {
  @Test
  public void testCodeNameEquals() {
    CodeName codeName1 = null;
    CodeName codeName2 = null;
    Assert.assertTrue(CodeNameUtil.equals(codeName1, codeName2));
    codeName1 = new CodeName("test");
    codeName2 = new CodeName("test");
    Assert.assertTrue(CodeNameUtil.equals(codeName1, codeName2));
    codeName2 = null;
    Assert.assertFalse(CodeNameUtil.equals(codeName1, codeName2));
    codeName2 = new CodeName("test2");
    Assert.assertFalse(CodeNameUtil.equals(codeName1, codeName2));
  }
}
