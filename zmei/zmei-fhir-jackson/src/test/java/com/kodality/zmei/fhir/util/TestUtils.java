package com.kodality.zmei.fhir.util;

import com.kodality.zmei.fhir.jackson.OffsetDateTimeParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import org.apache.commons.io.IOUtils;

public class TestUtils {
  private TestUtils() {}

  public static String fixDate(String json, String date) {
    String offsetDate = OffsetDateTimeParser.parse(date).format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
    return json.replace(offsetDate, date);
  }

  public static String readResource(String uri) throws IOException {
    InputStream is = TestUtils.class.getResourceAsStream(uri);
    return IOUtils.toString(is, StandardCharsets.UTF_8);
  }
}
