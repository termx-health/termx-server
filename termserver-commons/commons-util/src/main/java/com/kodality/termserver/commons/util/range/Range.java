package com.kodality.termserver.commons.util.range;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

@EqualsAndHashCode
@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class Range<T extends Comparable> {
  private T lower;
  private T upper;
  private boolean lowerInclusive = true;
  private boolean upperInclusive = true;

  private final static String INFINITY = "infinity";
  private static final Pattern pattern = Pattern.compile("(\\[|\\()\"?([^\"]*)\"?,\"?([^\"]*)?\"?(\\]|\\))");

  public Range() {}

  public Range(String range) {
    fromString(range);
  }

  protected abstract T parse(String input);

  protected abstract String format(T endpoint);

  public String asString() {
    String lowerSign = getLower() != null && isLowerInclusive() ? "[" : "(";
    String upperSign = getUpper() != null && isUpperInclusive() ? "]" : ")";
    String lowerValue = getLower() == null ? "" : format(getLower());
    String upperValue = getUpper() == null ? "" : format(getUpper());
    return lowerSign + lowerValue + "," + upperValue + upperSign;
  }

  @JsonIgnore
  public boolean isValid() {
    if (getLower() == null || getUpper() == null) {
      return true;
    }
    return getLower().compareTo(getUpper()) <= 0;
  }

  public boolean contains(T value) {
    if (value == null) {
      return false;
    }
    int l = ObjectUtils.compare(getLower(), value, false);
    int u = ObjectUtils.compare(value, getUpper(), true);
    return (isLowerInclusive() ? l <= 0 : l < 0) && (isUpperInclusive() ? u <= 0 : u < 0);
  }

  public boolean containsRange(Range<T> range) {
    boolean containsStart = getLower() == null && range.getLower() == null || contains(range.getLower());
    boolean containsEnd = getUpper() == null && range.getUpper() == null || contains(range.getUpper());
    return containsStart && containsEnd;
  }

  public boolean intersects(Range<T> range) {
    Range<T> first, second;
    if (ObjectUtils.compare(getLower(), range.getLower(), false) <= 0) {
      first = this;
      second = range;
    } else {
      first = range;
      second = this;
    }
    return second.getLower() == null || first.contains(second.getLower());
  }

  @JsonIgnore
  public boolean isEmpty() {
    if (getLower() == null || getUpper() == null || !getLower().equals(getUpper())) {
      return false;
    }
    return !lowerInclusive || !upperInclusive;
  }

  private void fromString(String rangeText) {
    if (StringUtils.isEmpty(rangeText)) {
      return;
    }
    Matcher matcher = pattern.matcher(rangeText.trim());
    if (!matcher.matches()) {
      throw new IllegalArgumentException("invalid range");
    }
    setLower(parseEndpoint(matcher.group(2)));
    setLowerInclusive(isInclusive(matcher.group(1)));
    setUpper(parseEndpoint(matcher.group(3)));
    setUpperInclusive(isInclusive(matcher.group(4)));
  }

  private T parseEndpoint(String input) {
    if (input == null || "".equals(input) || INFINITY.equals(input)) {
      return null;
    }
    return parse(input);
  }

  private static boolean isInclusive(String boundLiteral) {
    switch (boundLiteral) {
      case "(":
      case ")":
        return false;
      case "[":
      case "]":
        return true;
      default:
        throw new IllegalArgumentException("Unknown bound: " + boundLiteral);
    }
  }

  public T getLower() {
    return lower;
  }

  public void setLower(T lower) {
    this.lower = lower;
  }

  public void setLower(T lower, boolean inclusive) {
    this.lower = lower;
    this.lowerInclusive = inclusive;
  }

  public T getUpper() {
    return upper;
  }

  public void setUpper(T upper) {
    this.upper = upper;
  }

  public void setUpper(T upper, boolean inclusive) {
    this.upper = upper;
    this.upperInclusive = inclusive;
  }

  public boolean isLowerInclusive() {
    return lowerInclusive;
  }

  public void setLowerInclusive(boolean lowerInclusive) {
    this.lowerInclusive = lowerInclusive;
  }

  public boolean isUpperInclusive() {
    return upperInclusive;
  }

  public void setUpperInclusive(boolean upperInclusive) {
    this.upperInclusive = upperInclusive;
  }

  public void convert(boolean lowerInclusive, boolean upperInclusive) {
    throw new NotImplementedException("this method is not implemented");
  }

  public static <U extends Comparable> boolean hasAnyIntersection(List<? extends Range<U>> ranges) {
    Comparator<Range<U>> comparator = Comparator.nullsFirst(Comparator.comparing(Range::getLower));
    List<Range<U>> sorted = new ArrayList<>(ranges);
    sorted.sort(comparator);
    for (int i = 1; i < sorted.size(); i++) {
      Range<U> prev = sorted.get(i - 1);
      Range<U> next = sorted.get(i);
      if (prev.intersects(next)) {
        return true;
      }
    }
    return false;
  }

}
