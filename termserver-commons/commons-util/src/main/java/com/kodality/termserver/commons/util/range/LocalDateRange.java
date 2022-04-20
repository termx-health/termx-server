package com.kodality.termserver.commons.util.range;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class LocalDateRange extends Range<LocalDate> {

  public LocalDateRange() {}

  public LocalDateRange(String range) {
    super(range);
    convert(true, true);
  }

  public LocalDateRange(LocalDate from, LocalDate to) {
    setLower(from);
    setUpper(to);
    setLowerInclusive(true);
    setUpperInclusive(true);
  }

  public static LocalDateRange of(String range) {
    return range == null ? null : new LocalDateRange(range);
  }

  @Override
  protected LocalDate parse(String input) {
    return LocalDate.parse(input);
  }

  @Override
  protected String format(LocalDate date) {
    return date.format(DateTimeFormatter.ISO_LOCAL_DATE);
  }

  @Override
  public LocalDate getLower() {
    return super.getLower();
  }

  @Override
  public LocalDate getUpper() {
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

  @Override
  public void convert(boolean lowerInclusive, boolean upperInclusive) {
    if (lowerInclusive != isLowerInclusive()) {
      setLowerInclusive(lowerInclusive);
      if (getLower() != null) {
        setUpper(getLower().plusDays(lowerInclusive ? 1 : -1));
      }
    }
    if (upperInclusive != isUpperInclusive()) {
      setUpperInclusive(upperInclusive);
      if (getUpper() != null) {
        setUpper(getUpper().plusDays(upperInclusive ? -1 : 1));
      }
    }
  }

}
