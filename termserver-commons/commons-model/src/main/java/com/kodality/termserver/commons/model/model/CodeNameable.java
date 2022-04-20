package com.kodality.termserver.commons.model.model;

public interface CodeNameable {
  Long getId();

  String getCode();

  LocalizedName getNames();

  default CodeName strip() {
    return new CodeName(getId(), getCode(), getNames());
  }
}
