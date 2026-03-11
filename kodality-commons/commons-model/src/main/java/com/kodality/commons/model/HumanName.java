package com.kodality.commons.model;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;

@Getter
@Setter
@Accessors(chain = true)
public class HumanName {
  private String text;
  private String given;
  private String family;

  public HumanName() {
    //
  }

  public HumanName(String text) {
    this.text = text;
    if (text == null) {
      return;
    }
    if (text.contains(",")) {
      this.given = text.substring(text.indexOf(",") + 1).trim();
      this.family = text.substring(0, text.indexOf(",")).trim();
      return;
    }
    if (text.contains(" ")) {
      this.given = text.substring(0, text.indexOf(" ")).trim();
      this.family = text.substring(text.indexOf(" ") + 1).trim();
    }
  }

  public HumanName(String given, String family) {
    this.given = given;
    this.family = family;
    this.text = Stream.of(given, family).filter(n -> n != null).collect(joining(" "));
  }
}
