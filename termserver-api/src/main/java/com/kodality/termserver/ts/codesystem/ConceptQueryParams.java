package com.kodality.termserver.ts.codesystem;

import com.kodality.commons.model.QueryParams;
import java.time.LocalDate;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ConceptQueryParams extends QueryParams {
  private String id;
  private String code;
  private String codeContains;
  private String textContains;
  private String codeSystem;
  private List<String> permittedCodeSystems;
  private String codeSystemUri;
  private String codeSystemVersion;
  private Long codeSystemVersionId;
  private LocalDate codeSystemVersionReleaseDateLe;
  private LocalDate codeSystemVersionReleaseDateGe;
  private LocalDate codeSystemVersionExpirationDateLe;
  private LocalDate codeSystemVersionExpirationDateGe;
  private String codeSystemEntityStatus;
  private Long codeSystemEntityVersionId;
  private String valueSet;
  private String valueSetUri;
  private String valueSetVersion;
  private Long valueSetVersionId;
  private String valueSetExpandResultIds;
  private String propertyValues; //propertyName|value
  private String propertyValuesPartial; //propertyName|value
  private Long propertyRoot;
  private String associationRoot;
  private String associationLeaf;
  private String propertySource; //propertyId|sourceCode
  private String associationSource; //association|sourceCode
  private String associationTarget; //association|sourceCode
  private String associationSourceRecursive; //association|sourceCode
  private String associationTargetRecursive; //association|sourceCode
  private String associationType;
}