package com.kodality.termserver.common.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Objects;
import org.apache.commons.io.IOUtils;

public class TemplateUtil {

  public static String getTemplate(String fileName) {
    InputStream stream = TemplateUtil.class.getClassLoader().getResourceAsStream(fileName);
    try {
      return IOUtils.toString(Objects.requireNonNull(stream), Charset.defaultCharset());
    } catch (IOException e) {
      throw new RuntimeException("Unable to read template", e);
    }
  }

}
