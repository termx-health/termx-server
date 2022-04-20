package com.kodality.termserver.commons.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.function.BiFunction;

public final class DateUtil {
  public static final DateTimeFormatter PG_TIMESTAMP = new DateTimeFormatterBuilder()
      .appendPattern("yyyy-MM-dd HH:mm:ss")
      .appendFraction(ChronoField.MICRO_OF_SECOND, 0, 6, true)
      .toFormatter();
  public static final DateTimeFormatter PG_TIMESTAMP_X = new DateTimeFormatterBuilder()
      .appendPattern("yyyy-MM-dd HH:mm:ss")
      .appendFraction(ChronoField.MICRO_OF_SECOND, 0, 6, true)
      .appendPattern("X")
      .toFormatter();

  private DateUtil() {
  }

  public static LocalDate parseDate(String date) {
    return parseDate(date, DateTimeFormatter.ISO_DATE);
  }

  public static LocalTime parseTime(String date) {
    return parseTime(date, DateTimeFormatter.ISO_TIME);
  }

  public static LocalDateTime parseDateTime(String date) {
    return parseDateTime(date,
        DateTimeFormatter.ISO_OFFSET_DATE_TIME,
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        DateTimeFormatter.ISO_DATE_TIME,
        PG_TIMESTAMP,
        PG_TIMESTAMP_X);
  }

  public static OffsetDateTime parseOffsetDateTime(String date) {
    return parseOffsetDateTime(date,
        DateTimeFormatter.ISO_OFFSET_DATE_TIME,
        DateTimeFormatter.ISO_DATE_TIME,
        PG_TIMESTAMP,
        PG_TIMESTAMP_X);
  }

  @Deprecated
  public static ZonedDateTime parseZonedDateTime(String date) {
    return parseZonedDateTime(date,
        DateTimeFormatter.ISO_OFFSET_DATE_TIME,
        DateTimeFormatter.ISO_DATE_TIME,
        PG_TIMESTAMP,
        PG_TIMESTAMP_X);
  }

  public static LocalDate parseDate(String date, DateTimeFormatter... formats) {
    return parse(date, formats, (d, format) -> LocalDate.parse(d, format));
  }

  public static LocalTime parseTime(String date, DateTimeFormatter... formats) {
    return parse(date, formats, (d, format) -> LocalTime.parse(d, format));
  }

  public static LocalDateTime parseDateTime(String date, DateTimeFormatter... formats) {
    return parse(date, formats, (d, format) -> {
      return ZonedDateTime.parse(d, format.withZone(ZoneId.systemDefault()))
          .withZoneSameInstant(ZoneId.systemDefault())
          .toLocalDateTime();
    });
  }

  public static OffsetDateTime parseOffsetDateTime(String date, DateTimeFormatter... formats) {
    return parse(date, formats, (d, format) -> OffsetDateTime.parse(d, format));
  }

  @Deprecated
  public static ZonedDateTime parseZonedDateTime(String date, DateTimeFormatter... formats) {
    return parse(date, formats, (d, format) -> {
      return ZonedDateTime.parse(d, format.withZone(ZoneId.systemDefault()))
          .withZoneSameInstant(ZoneId.systemDefault());
    });
  }

  private static <T> T parse(String date, DateTimeFormatter[] formats, BiFunction<String, DateTimeFormatter, T> parseFn) {
    if (date == null) {
      return null;
    }
    for (DateTimeFormatter format : formats) {
      try {
        return parseFn.apply(date, format);
      } catch (DateTimeParseException e) {
        // next
      }
    }
    throw new DateTimeParseException("could not parse date '" + date + "'", date, 0);
  }

}
