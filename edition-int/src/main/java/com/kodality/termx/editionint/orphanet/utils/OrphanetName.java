package com.kodality.termx.editionint.orphanet.utils;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrphanetName {
  @JacksonXmlProperty
  private String lang;

  @JacksonXmlText
  private String value;
}
