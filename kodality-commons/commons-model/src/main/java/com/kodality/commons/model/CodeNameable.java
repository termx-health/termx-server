package com.kodality.commons.model;

public interface CodeNameable {
  Long getId();

  String getCode();

  LocalizedName getNames();

  public default CodeName strip() {
    return new CodeName(getId(), getCode(), getNames());
  }
}
