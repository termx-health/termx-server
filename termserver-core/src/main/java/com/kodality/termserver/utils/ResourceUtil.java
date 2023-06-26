package com.kodality.termserver.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Objects;
import org.apache.commons.io.IOUtils;

public class ResourceUtil {

  public static String readAsString(String fileName) {
    InputStream stream = ResourceUtil.class.getClassLoader().getResourceAsStream(fileName);
    try {
      return IOUtils.toString(Objects.requireNonNull(stream), Charset.defaultCharset());
    } catch (IOException e) {
      throw new RuntimeException("Unable to read template", e);
    }
  }

}
