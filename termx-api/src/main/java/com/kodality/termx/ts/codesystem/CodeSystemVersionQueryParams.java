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
public class CodeSystemVersionQueryParams extends QueryParams {
  private List<String> permittedCodeSystems;

  private String codeSystem; // eq, multiple
  private String codeSystemUri; // eq
  private String codeSystemContent;
  // name
  private String codeSystemName;
  private String codeSystemNameStarts;
  private String codeSystemNameContains;
  // publisher
  private String codeSystemPublisher;
  private String codeSystemPublisherStarts;
  private String codeSystemPublisherContains;
  // title
  private String codeSystemTitle;
  private String codeSystemTitleStarts;
  private String codeSystemTitleContains;
  // description
  private String codeSystemDescription;
  private String codeSystemDescriptionStarts;
  private String codeSystemDescriptionContains;

  private String conceptCode;


  private String ids;
  private String version;
  private String identifier; // eq, csv.identifier OR cs.identifier
  private String status;
  private LocalDate releaseDate;
  private LocalDate releaseDateLe;
  private LocalDate releaseDateGe;
  private LocalDate expirationDateLe;
  private LocalDate expirationDateGe;
}
