package com.kodality.termserver.commons.util.range;

import com.kodality.termserver.commons.util.DateUtil;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public class LocalDateTimeRange extends Range<LocalDateTime> {

  public LocalDateTimeRange() {}

  public LocalDateTimeRange(String range) {
    super(range);
  }

  public LocalDateTimeRange(LocalDateTime from, LocalDateTime to) {
    setLower(from);
    setUpper(to);
    setLowerInclusive(true);
    setUpperInclusive(true);
  }

  @Override
  protected LocalDateTime parse(String input) {
    return DateUtil.parseDateTime(input);
  }

  @Override
  protected String format(LocalDateTime date) {
    return date.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
  }

  public Long diff(ChronoUnit unit) {
    if (getLower() == null || getUpper() == null) {
      return null;
    }
    return getLower().until(getUpper(), unit);
  }

}
