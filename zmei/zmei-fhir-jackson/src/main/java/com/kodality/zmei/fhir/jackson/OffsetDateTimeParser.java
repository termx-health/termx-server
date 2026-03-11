package com.kodality.zmei.fhir.jackson;

import java.time.OffsetDateTime;
import java.time.ZonedDateTime;

public class OffsetDateTimeParser {

  public static OffsetDateTime parse(String input) {
    ZonedDateTime zdt = ZonedDateTimeParser.parse(input);
    return zdt == null ? null : zdt.toOffsetDateTime();
  }

}
