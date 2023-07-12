package com.kodality.termx.ts.valueset;

import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.termx.ts.codesystem.Designation;
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
  private Long conceptVersionId;
  private Designation display;
  private List<Designation> additionalDesignations;
  private Integer orderNumber;

  private boolean active;
}