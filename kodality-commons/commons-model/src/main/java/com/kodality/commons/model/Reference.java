package com.kodality.commons.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class Reference {
  private String type;
  private String id;
  private String display;

  public Reference(String type, String id) {
    this.type = type;
    this.id = id;
  }

  @JsonIgnore
  public Long getIdLong() {
    return id == null ? null : Long.valueOf(id);
  }
}
