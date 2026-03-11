package com.kodality.commons.util.range;

public class IntRange extends Range<Integer> {

  public IntRange(String range) {
    super(range);
  }

  public IntRange() {

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
