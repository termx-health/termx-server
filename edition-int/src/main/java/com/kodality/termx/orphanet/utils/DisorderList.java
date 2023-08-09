package com.kodality.termx.orphanet.utils;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DisorderList {
  @JacksonXmlProperty(localName = "count")
  private Integer count;

  @JacksonXmlProperty(localName = "Disorder")
  private List<OrphanetDisorder> disorders;

}
