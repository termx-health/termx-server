package com.kodality.termserver.valueset;

import com.kodality.termserver.codesystem.Concept;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ValueSet {
  private Long id;
  private String name;
  private String rule;
  private String description;
  private String status;

  private List<Concept> concepts;

  private ValueSetVersion version;
}
