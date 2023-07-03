package com.kodality.termx.ts.valueset;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ValueSetSnapshot {
  private Long id;
  private String valueSet;
  private ValueSetVersionReference valueSetVersion;
  private Integer conceptsTotal;
  private List<ValueSetVersionConcept> expansion;
  private OffsetDateTime createdAt;
  private String createdBy;
}
