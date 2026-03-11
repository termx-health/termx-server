package com.kodality.commons.util;

import org.junit.Assert;
import org.junit.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.TimeZone;

public class DateUtilTest {

  @Test
  public void dateTimeFormats() {
    TimeZone.setDefault(TimeZone.getTimeZone("Europe/Tallinn"));
    
    test("2010-10-10T10:10:10", "20101010-1010");
    test("2010-10-10 10:10:10", "20101010-1010");
    test("2010-10-10 10:10:10.101", "20101010-1010");
    test("2010-10-10 10:10:10.1010", "20101010-1010");
    test("2010-10-10 10:10:10.10101", "20101010-1010");
    test("2010-10-10 10:10:10.101010", "20101010-1010");
    
    test("2010-10-10T03:10:10-07", "20101010-1310");
    test("2010-10-10T10:10:10Z", "20101010-1310");
    test("2010-10-10T10:10:10+03", "20101010-1010");
    test("2010-10-10 10:10:10.1010+03", "20101010-1010");
    try {
      DateUtil.parseDateTime("123");
    } catch (DateTimeParseException e) {
      //
    }
  }

  private void test(String input, String expected) {
    LocalDateTime date = DateUtil.parseDateTime(input);
    Assert.assertEquals(expected, date.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")));
  }

}
