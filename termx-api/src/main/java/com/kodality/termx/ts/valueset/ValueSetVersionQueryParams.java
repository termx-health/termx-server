package com.kodality.termx.ts.valueset;

import com.kodality.commons.model.QueryParams;
import java.time.LocalDate;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ValueSetVersionQueryParams extends QueryParams {
  private String valueSet;
  private String valueSetUri;
  private String valueSetName;
  private String valueSetNameContains;
  private String valueSetNameStarts;
  private String valueSetTitle;
  private String valueSetTitleContains;
  private String valueSetPublisher;
  private String valueSetDescriptionContains;
  private List<String> permittedValueSets;
  private String codeSystemUri;
  private String conceptCode;
  private String ids;
  private String version;
  private String status;
  private LocalDate releaseDateLe;
  private LocalDate expirationDateGe;
}
