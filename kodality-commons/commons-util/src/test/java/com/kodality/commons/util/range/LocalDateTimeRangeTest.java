package com.kodality.commons.util.range;

import org.junit.Assert;
import org.junit.Test;

import java.time.LocalDateTime;

public class LocalDateTimeRangeTest {

  @Test
  public void rangeTest() {
    test(null, null, null);
    test("(,)", null, null);
    test("(infinity,infinity)", null, null);
    test("[\"2018-12-12 10:00:00\",]", LocalDateTime.parse("2018-12-12T10:00:00"), null);
    test("[\"2018-12-12 10:00:00\",\"2018-12-12 12:00:00\")", LocalDateTime.parse("2018-12-12T10:00:00"), LocalDateTime.parse("2018-12-12T12:00:00"));
  }

  private void test(String range, LocalDateTime from, LocalDateTime to) {
    LocalDateTimeRange r = new LocalDateTimeRange(range);
    Assert.assertEquals(from, r.getLower());
    Assert.assertEquals(to, r.getUpper());
  }
}
