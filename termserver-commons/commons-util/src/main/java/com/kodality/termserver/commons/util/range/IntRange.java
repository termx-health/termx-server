package com.kodality.termserver.commons.util.range;

public class IntRange extends Range<Integer> {

  public IntRange() {}

  public IntRange(String range) {
    super(range);
  }

  @Override
  protected Integer parse(String input) {
    return Integer.valueOf(input);
  }

  @Override
  protected String format(Integer endpoint) {
    return endpoint.toString();
  }

}
