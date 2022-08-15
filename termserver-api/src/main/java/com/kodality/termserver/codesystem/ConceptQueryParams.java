package com.kodality.termserver.codesystem;

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
  private String propertyValues; //propertyName|value
  private String propertyValuesPartial; //propertyName|value
}
