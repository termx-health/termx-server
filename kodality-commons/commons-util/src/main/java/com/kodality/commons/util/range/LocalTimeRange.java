package com.kodality.commons.util.range;

import com.kodality.commons.util.DateUtil;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class LocalTimeRange extends Range<LocalTime> {
  public LocalTimeRange() {
    //
  }

  public LocalTimeRange(String range) {
    super(range);
  }


  public LocalTimeRange(LocalTime from, LocalTime to) {
    setLower(from);
    setUpper(to);
    setLowerInclusive(true);
    setUpperInclusive(true);
  }

  @Override
  protected LocalTime parse(String input) {
    return DateUtil.parseTime(input);
  }

  @Override
  protected String format(LocalTime date) {
    return date.format(DateTimeFormatter.ISO_TIME);
  }

}
