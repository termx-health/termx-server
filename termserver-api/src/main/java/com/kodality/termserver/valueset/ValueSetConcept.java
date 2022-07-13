package com.kodality.termserver.valueset;

import com.kodality.termserver.codesystem.Concept;
import com.kodality.termserver.codesystem.Designation;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ValueSetConcept {
  private Long id;
  private Concept concept;
  private Designation display;
  private List<Designation> additionalDesignations;
}
