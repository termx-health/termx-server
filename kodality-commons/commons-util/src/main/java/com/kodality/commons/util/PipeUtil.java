package com.kodality.commons.util;

import com.kodality.commons.model.Identifier;
import org.apache.commons.lang3.StringUtils;

public class PipeUtil {

  private static final String DELIMITER = "|";

  /**
   * @return [key, value]
   */
  public static String[] parsePipe(String pipeParam) {
    if (StringUtils.isEmpty(pipeParam)) {
      return new String[2];
    }
    if (!pipeParam.contains(DELIMITER)) {
      return new String[]{null, pipeParam};
    }
    String one = StringUtils.substringBefore(pipeParam, DELIMITER);
    String two = StringUtils.substringAfter(pipeParam, DELIMITER);
    return new String[]{ one, StringUtils.isEmpty(two) ? null : two };
  }

  public static String toPipe(String... args) {
    if (args.length < 1) {
      throw new IllegalStateException("Need at least one arg");
    }
    if (args.length == 1) {
      return args[0];
    }
    if (args[0] == null && args[1] != null) {
      return args[1];
    }
    if (args[0] == null) {
      return null;
    }
    return args[0] + DELIMITER + args[1];
  }

  public static String toPipe(String system, String value) {
    return toPipe(new String[]{system, value});
  }

  public static String toPipe(Identifier identifier) {
    if (identifier == null) {
      return null;
    }
    return toPipe(new String[]{identifier.getSystem(), identifier.getValue()});
  }
}
