package com.kodality.zmei.fhir.jackson;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class ZonedDateTimeParser {

  private static final DateTimeFormatter YEAR = DateTimeFormatter.ofPattern("yyyy");
  private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

  private static final List<Function<String, ZonedDateTime>> parsers = new ArrayList<>();

  static {
    parsers.add(ZonedDateTimeParser::parseZonedDateTime);
    parsers.add(ZonedDateTimeParser::parseLocalDate);
    parsers.add(ZonedDateTimeParser::parseYearMonth);
    parsers.add(ZonedDateTimeParser::parseYear);
  }

  public static ZonedDateTime parse(String input) {
    if (input == null) {
      return null;
    }

    for (Function<String, ZonedDateTime> parser : parsers) {
      try {
        return parser.apply(input);
      } catch (DateTimeParseException e) {
        // next
      }
    }
    throw new DateTimeParseException("Could not parse input '" + input + "' to zoned date time", input, 0);
  }

  private static ZonedDateTime parseZonedDateTime(String input) {
    return ZonedDateTime.parse(input);
  }

  private static ZonedDateTime parseLocalDate(String input) {
    LocalDate localDate = LocalDate.parse(input);
    return ZonedDateTime.of(localDate, LocalTime.MIN, ZoneId.systemDefault());
  }

  private static ZonedDateTime parseYearMonth(String input) {
    TemporalAccessor accessor = YEAR_MONTH.parse(input);
    LocalDate localDate = LocalDate.of(accessor.get(ChronoField.YEAR), accessor.get(ChronoField.MONTH_OF_YEAR), 1);
    return ZonedDateTime.of(localDate, LocalTime.MIN, ZoneId.systemDefault());
  }

  private static ZonedDateTime parseYear(String input) {
    TemporalAccessor accessor = YEAR.parse(input);
    LocalDate localDate = LocalDate.of(accessor.get(ChronoField.YEAR), 1, 1);
    return ZonedDateTime.of(localDate, LocalTime.MIN, ZoneId.systemDefault());
  }

}
