package com.kodality.commons.util;

import com.kodality.commons.model.CodeName;
import org.apache.commons.lang3.StringUtils;

public final class CodeNameUtil {
  private CodeNameUtil() {}

  public static boolean equals(CodeName codeName1, CodeName codeName2) {
    if (codeName1 == codeName2) {
      return true;
    }
    if (codeName1 == null || codeName2 == null) {
      return false;
    }
    return StringUtils.equals(codeName1.getCode(), codeName2.getCode());
  }
}
