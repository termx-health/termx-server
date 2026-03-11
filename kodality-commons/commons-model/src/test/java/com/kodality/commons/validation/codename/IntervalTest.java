package com.kodality.commons.validation.codename;

import com.kodality.commons.model.Interval;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.Assert;
import org.junit.Test;

public class IntervalTest {

  @Test
  public void intervalFormats() {
    testDateTime("2019-09-10T10:10:10", "2019-09-24T10:10:15", "0 years 0 months 14 days 0 hours 0 minutes 5 seconds");
    testDateTime("2019-09-10T10:10:10", "2019-10-10T10:20:10", "0 years 1 months 0 days 0 hours 10 minutes 0 seconds");
    testDateTime("2019-09-10T10:10:10", "2020-09-10T11:10:10", "1 years 0 months 0 days 1 hours 0 minutes 0 seconds");
    testDateTime("2019-09-10T10:10:10", "2020-12-15T10:10:10", "1 years 3 months 5 days 0 hours 0 minutes 0 seconds");
    testDateTime("2019-09-10T10:10:10", "2019-10-10T10:10:10", "0 years 1 months 0 days 0 hours 0 minutes 0 seconds");
    testDateTime("2019-09-10T10:10:10", "2019-10-09T10:10:10", "0 years 0 months 29 days 0 hours 0 minutes 0 seconds");

    testDateTime("2019-09-10T10:10:10", "2019-09-09T09:10:10", "0 years 0 months -1 days -1 hours 0 minutes 0 seconds");
    testDateTime("2019-09-10T10:10:10", "2019-06-10T10:02:10", "0 years -3 months 0 days 0 hours -8 minutes 0 seconds");
    testDateTime("2019-09-10T10:10:10", "2019-06-05T10:10:10", "0 years -3 months -5 days 0 hours 0 minutes 0 seconds");
    testDateTime("2019-09-10T10:10:10", "2017-05-25T10:10:10", "-2 years -3 months -16 days 0 hours 0 minutes 0 seconds");

    testDate("2019-09-10", "2019-09-24", "0 years 0 months 14 days 0 hours 0 minutes 0 seconds");
    testDate("2019-09-10", "2019-10-10", "0 years 1 months 0 days 0 hours 0 minutes 0 seconds");
    testDate("2019-09-10", "2020-09-10", "1 years 0 months 0 days 0 hours 0 minutes 0 seconds");
    testDate("2019-09-10", "2020-12-15", "1 years 3 months 5 days 0 hours 0 minutes 0 seconds");
    testDate("2019-09-10", "2019-10-10", "0 years 1 months 0 days 0 hours 0 minutes 0 seconds");
    testDate("2019-09-10", "2019-10-09", "0 years 0 months 29 days 0 hours 0 minutes 0 seconds");

    testDate("2019-09-10", "2019-09-09", "0 years 0 months -1 days 0 hours 0 minutes 0 seconds");
    testDate("2019-09-10", "2019-06-10", "0 years -3 months 0 days 0 hours 0 minutes 0 seconds");
    testDate("2019-09-10", "2019-06-05", "0 years -3 months -5 days 0 hours 0 minutes 0 seconds");
    testDate("2019-09-10", "2017-05-25", "-2 years -3 months -16 days 0 hours 0 minutes 0 seconds");
  }
  
  @Test
  public void plusMinus() {
    Interval interval = new Interval(1, 2, 3, 4, 5, 6);
    LocalDateTime time = LocalDateTime.of(2000, 01, 01, 00, 00, 00);
    Assert.assertEquals(interval.addTo(time), LocalDateTime.of(2001, 03, 04, 04, 05, 06));
    Assert.assertEquals(interval.subtractFrom(time), LocalDateTime.of(1998, 10, 28, 19, 54, 54));
  }

  private void testDateTime(String from, String to, String expected) {
    LocalDateTime date1 = LocalDateTime.parse(from);
    LocalDateTime date2 = LocalDateTime.parse(to);
    Interval interval = new Interval(date1, date2);
    Assert.assertEquals(expected, interval.asString());
  }

  private void testDate(String from, String to, String expected) {
    LocalDate date1 = LocalDate.parse(from);
    LocalDate date2 = LocalDate.parse(to);
    Interval interval = new Interval(date1, date2);
    Assert.assertEquals(expected, interval.asString());
  }
  
}
