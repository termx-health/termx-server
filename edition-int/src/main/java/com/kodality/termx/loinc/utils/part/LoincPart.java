package com.kodality.termx.loinc.utils.part;

import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class LoincPart {
  private String code;
  private Map<String, String> display;
  private String alias;
  private String type;
}
