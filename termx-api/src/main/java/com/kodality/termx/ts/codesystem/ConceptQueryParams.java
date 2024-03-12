package com.kodality.termx.ts.codesystem;

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
  private String codeEq;
  private List<String> codes;
  private String codeContains;
  private String textEq;
  private String textContains;
  private String textContainsSep;
  private String codeSystem;
  private List<String> permittedCodeSystems;
  private String codeSystemUri;
  private String codeSystemVersion;
  private String codeSystemVersionCodeSystem;
  private Long codeSystemVersionId;
  private String codeSystemVersions; //cs1|v1,cs2|v1
  private LocalDate codeSystemVersionReleaseDateLe;
  private LocalDate codeSystemVersionReleaseDateGe;
  private LocalDate codeSystemVersionExpirationDateLe;
  private LocalDate codeSystemVersionExpirationDateGe;
  private String codeSystemEntityStatus;
  private String codeSystemEntityVersionId;
  private String properties; //propertyId1,propertyId2
  private String propertyValues; //propertyName|value1,value2;propertyName|value1
  private String propertyValuesPartial; //propertyName|value1,value2;propertyName|value1
  private Long propertyRoot;
  private String associationRoot;
  private String associationLeaf;
  private String propertySource; //propertyId|sourceCode
  private String associationSource; //association|sourceCode
  private String associationTarget; //association|sourceCode
  private String associationSourceRecursive; //association|sourceCode
  private String associationTargetRecursive; //association|sourceCode
  private String associationType;
  private String designationCiEq;
  private Long unmapedInMapSetVersionId;
  private Long verifiedInMapSetVersionId;
  private Long unverifiedInMapSetVersionId;
}
