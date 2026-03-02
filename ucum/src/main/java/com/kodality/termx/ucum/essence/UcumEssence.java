package com.kodality.termx.ucum.essence;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Introspected
@Accessors(chain = true)
public class UcumEssence {
  private Long id;
  private String version;
  private String xml;
}
