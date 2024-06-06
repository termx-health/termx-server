package com.kodality.termx.ts.valueset;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ValueSetCompareResult {
  private List<String> added = new ArrayList<>();
  private List<String> deleted = new ArrayList<>();
}
