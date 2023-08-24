package com.kodality.termx.ts.valueset;

import com.kodality.termx.ts.codesystem.CodeSystemAssociation;
import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.termx.ts.codesystem.Designation;
import com.kodality.termx.ts.codesystem.EntityPropertyValue;
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
  private ValueSetVersionConceptValue concept;
  private Designation display;
  private List<Designation> additionalDesignations;
  private Integer orderNumber;

  private boolean enumerated; //calculated field
  private boolean active; //calculated field
  private List<CodeSystemAssociation> associations; //decorated field
  private List<EntityPropertyValue> propertyValues; //decorated field

  @Getter
  @Setter
  public static class ValueSetVersionConceptValue {
    private Long conceptVersionId;
    private String code;
    private String codeSystem;
    private String codeSystemUri;
    private String baseCodeSystemUri;

    private List<String> codeSystemVersions; //decorated field

    public static ValueSetVersionConceptValue fromConcept(Concept c) {
      return new ValueSetVersionConceptValue().setCode(c.getCode()).setCodeSystem(c.getCodeSystem());
    }
  }
}
