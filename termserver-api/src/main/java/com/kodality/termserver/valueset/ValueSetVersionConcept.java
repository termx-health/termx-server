package com.kodality.termserver.valueset;

import com.kodality.termserver.codesystem.Concept;
import com.kodality.termserver.codesystem.Designation;
import io.micronaut.core.annotation.Introspected;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Introspected
@Accessors(chain = true)
public class ValueSetVersionConcept {
  private Long id;
  private Concept concept;
  private Designation display;
  private List<Designation> additionalDesignations;

  private boolean active;
}
