package com.kodality.termx.ts.mapset;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class MapSetPropertyRule {
  private List<String> codeSystems;
  private String valueSet;}
