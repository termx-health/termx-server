package com.kodality.zmei.fhir.jackson;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class OffsetDateTimeParserTest {

  @Test
  public void shouldParseOffsetDateTime() {
    OffsetDateTime expected = ZonedDateTime.of(LocalDate.of(2019, 11, 19), LocalTime.of(19, 5, 23), ZoneId.systemDefault()).toOffsetDateTime();
    OffsetDateTime parsed = OffsetDateTimeParser.parse(expected.toString());
    assertEquals(parsed, expected);
  }

  @Test
  public void shouldParseLocalDate() {
    OffsetDateTime expected = ZonedDateTime.of(LocalDate.of(2019, 11, 19), LocalTime.of(0, 0, 0), ZoneId.systemDefault()).toOffsetDateTime();
    OffsetDateTime parsed = OffsetDateTimeParser.parse("2019-11-19");
    assertEquals(parsed, expected);
  }

  @Test
  public void shouldParseYearMonth() {
    OffsetDateTime expected = ZonedDateTime.of(LocalDate.of(2019, 11, 1), LocalTime.of(0, 0, 0), ZoneId.systemDefault()).toOffsetDateTime();
    OffsetDateTime parsed = OffsetDateTimeParser.parse("2019-11");
    assertEquals(parsed, expected);
  }

  @Test
  public void shouldParseYear() {
    OffsetDateTime expected = ZonedDateTime.of(LocalDate.of(2019, 1, 1), LocalTime.of(0, 0, 0), ZoneId.systemDefault()).toOffsetDateTime();
    OffsetDateTime parsed = OffsetDateTimeParser.parse("2019");
    assertEquals(parsed, expected);
  }

}
