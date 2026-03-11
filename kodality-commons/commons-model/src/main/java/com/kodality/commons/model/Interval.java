package com.kodality.commons.model;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Interval {
  private int years = 0;
  private int months = 0;
  private int days = 0;
  private int hours = 0;
  private int minutes = 0;
  private int seconds = 0;

  public Interval(int years, int months, int days) {
    this.years = years;
    this.months = months;
    this.days = days;
  }

  public Interval(int years, int months, int days, int hours, int minutes) {
    this.years = years;
    this.months = months;
    this.days = days;
    this.hours = hours;
    this.minutes = minutes;
  }

  public Interval(LocalDateTime date1, LocalDateTime date2) {
    this.calculateInterval(date1, date2);
  }

  public Interval(LocalDate date1, LocalDate date2) {
    this.calculateInterval(date1.atStartOfDay(), date2.atStartOfDay());
  }

  private void calculateInterval(LocalDateTime date1, LocalDateTime date2) {
    years = Math.toIntExact(ChronoUnit.YEARS.between(date1, date2));
    date1 = date1.plusYears(years);
    months = Math.toIntExact(ChronoUnit.MONTHS.between(date1, date2));
    date1 = date1.plusMonths(months);
    days = Math.toIntExact(ChronoUnit.DAYS.between(date1, date2));
    date1 = date1.plusDays(days);
    hours = Math.toIntExact(ChronoUnit.HOURS.between(date1, date2));
    date1 = date1.plusHours(hours);
    minutes = Math.toIntExact(ChronoUnit.MINUTES.between(date1, date2));
    date1 = date1.plusMinutes(minutes);
    seconds = Math.toIntExact(ChronoUnit.SECONDS.between(date1, date2));
  }

  public LocalDateTime addTo(LocalDateTime time) {
    if (time == null) {
      return null;
    }
    return time.plus(asPeriod()).plus(asDuration());
  }

  public LocalDateTime subtractFrom(LocalDateTime time) {
    if (time == null) {
      return null;
    }
    return time.minus(asPeriod()).minus(asDuration());
  }

  public Period asPeriod() {
    return Period.of(getYears(), getMonths(), getDays());
  }

  public Duration asDuration() {
    return Duration.ofHours(getHours()).plusMinutes(getMinutes()).plusSeconds(getSeconds());
  }

  public String asString() {
    return String
        .format("%d years %d months %d days %d hours %d minutes %d seconds", years, months, days, hours, minutes, seconds);
  }
}
