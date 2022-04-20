package com.kodality.termserver.commons.util.range;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DateRange extends Range<Date> {
  public static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ssX";

  public DateRange() {}

  public DateRange(String range) {
    super(range);
  }

  public DateRange(Date from, Date to) {
    setLower(from);
    setUpper(to);
    setLowerInclusive(true);
    setUpperInclusive(true);
  }

  @Override
  protected Date parse(String input) {
    try {
      return new SimpleDateFormat(DATE_FORMAT).parse(input);
    } catch (ParseException e) {
      throw new IllegalArgumentException("Invalid date format: " + input);
    }
  }

  @Override
  protected String format(Date endpoint) {
    return new SimpleDateFormat(DATE_FORMAT).format(endpoint);
  }

  @Override
  public Date getLower() {
    return super.getLower();
  }

  @Override
  public Date getUpper() {
    return super.getUpper();
  }

  @JsonIgnore
  @Override
  public boolean isLowerInclusive() {
    return super.isLowerInclusive();
  }

  @JsonIgnore
  @Override
  public boolean isUpperInclusive() {
    return super.isUpperInclusive();
  }

}
