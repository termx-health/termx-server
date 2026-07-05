package org.termx.ucum.ts;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.termx.ts.codesystem.Designation;

@Getter
@Setter
@Accessors(chain = true)
public class UcumUnitDefinition {
  private String code;
  private String kind;
  private String property;
  /** Base (UCUM, English) display names — from the essence file. */
  private List<String> names;
  /** Supplement-contributed designations, carrying their real language + type (et/ru display, aliases, …). */
  private List<Designation> supplementDesignations;
}
