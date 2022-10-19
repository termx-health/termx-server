package com.kodality.termserver.thesaurus.structuredefinition;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class StructureDefinition {
  private Long id;
  private String url;
  private String code;
  private String content;
  private String contentType;
  private String contentFormat;
}
