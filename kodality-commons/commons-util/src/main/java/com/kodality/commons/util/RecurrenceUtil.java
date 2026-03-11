package com.kodality.commons.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import org.dmfs.rfc5545.DateTime;
import org.dmfs.rfc5545.recur.Freq;
import org.dmfs.rfc5545.recur.InvalidRecurrenceRuleException;
import org.dmfs.rfc5545.recur.RecurrenceRule;
import org.dmfs.rfc5545.recur.RecurrenceRule.Part;
import org.dmfs.rfc5545.recur.RecurrenceRuleIterator;

public class RecurrenceUtil {
  public static String FREQ = "DAILY";
  public static int INTERVAL = 1;

  public static List<LocalDateTime> findRecurrenceDates(List<Integer> hours, LocalDateTime start, LocalDateTime end) {
    try {
      RecurrenceRule rule = new RecurrenceRule(Freq.valueOf(FREQ));
      rule.setInterval(INTERVAL);
      rule.setUntil(toDateTime(end));
      rule.setByPart(Part.BYHOUR, hours);
      rule.setByPart(Part.BYMINUTE, 0);
      rule.setByPart(Part.BYSECOND, 0);
      return findRecurrenceDates(rule, start);
    } catch (InvalidRecurrenceRuleException e) {
      throw new RuntimeException("Invalid rule: " + e.getMessage());
    }
  }

  public static List<LocalDateTime> findRecurrenceDates(String ruleLine, LocalDateTime start, LocalDateTime end) {
    try {
      RecurrenceRule rule = new RecurrenceRule(ruleLine);
      if (end != null) {
        rule.setUntil(toDateTime(end));
      }
      return findRecurrenceDates(rule, start);
    } catch (InvalidRecurrenceRuleException e) {
      throw new RuntimeException("Invalid rule (" + ruleLine + ") : " + e.getMessage());
    }
  }


  public static List<LocalDateTime> findRecurrenceDates(RecurrenceRule rule, LocalDateTime start) {
    if (rule.isInfinite()) {
      throw new RuntimeException("Infinite rule");
    }
    RecurrenceRuleIterator it = rule.iterator(toDateTime(start));

    List<LocalDateTime> dates = new ArrayList<>();
    while (it.hasNext()) {
      dates.add(toLocalDateTime(it.nextMillis()));
    }
    return dates;

  }

  private static DateTime toDateTime(LocalDateTime ldt) {
    long timestamp = ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    return new DateTime(DateTime.GREGORIAN_CALENDAR_SCALE, TimeZone.getDefault(), timestamp);
  }


  private static LocalDateTime toLocalDateTime(Long millis) {
    return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDateTime();
  }

}
