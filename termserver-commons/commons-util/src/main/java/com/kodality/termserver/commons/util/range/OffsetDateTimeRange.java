package com.kodality.termserver.commons.util.range;

import com.kodality.termserver.commons.util.DateUtil;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import org.apache.commons.lang3.ObjectUtils;

public class OffsetDateTimeRange extends Range<OffsetDateTime> {

  public OffsetDateTimeRange() {}

  public OffsetDateTimeRange(String range) {
    super(range);
  }

  public OffsetDateTimeRange(OffsetDateTime from, OffsetDateTime to) {
    setLower(from);
    setUpper(to);
    setLowerInclusive(true);
    setUpperInclusive(true);
  }

  public boolean contains(OffsetDateTime value, TemporalUnit precision) {
    if (value == null) {
      return false;
    }
    value = value.truncatedTo(precision);
    OffsetDateTime lower = getLower() == null ? null : getLower().truncatedTo(precision);
    OffsetDateTime upper = getUpper() == null ? null : getUpper().truncatedTo(precision);
    int l = ObjectUtils.compare(lower, value, false);
    int u = ObjectUtils.compare(value, upper, true);
    return (isLowerInclusive() ? l <= 0 : l < 0) && (isUpperInclusive() ? u <= 0 : u < 0);
  }

  @Override
  protected OffsetDateTime parse(String input) {
    return DateUtil.parseOffsetDateTime(input);
  }

  @Override
  protected String format(OffsetDateTime date) {
    return date.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
  }

  public Long diff(ChronoUnit unit) {
    if (getLower() == null || getUpper() == null) {
      return null;
    }
    return getLower().until(getUpper(), unit);
  }

}
