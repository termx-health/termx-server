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
  private List<String> permittedValueSets;

  private String valueSet; // eq
  private String valueSetUri; // eq
  private String valueSetIdentifier; // eq
  // name
  private String valueSetName; // eq
  private String valueSetNameStarts;
  private String valueSetNameContains;
  // publisher
  private String valueSetPublisher; // eq
  private String valueSetPublisherStarts;
  private String valueSetPublisherContains;
  // title
  private String valueSetTitle; // eq
  private String valueSetTitleStarts;
  private String valueSetTitleContains;
  // description
  private String valueSetDescription; // eq
  private String valueSetDescriptionStarts;
  private String valueSetDescriptionContains;

  private String conceptCode; // eq
  private String codeSystemUri; // eq

  private String ids; // eq, multiple
  private String version; // eq
  private String status; // eq
  private LocalDate releaseDate;
  private LocalDate releaseDateLe;
  private LocalDate releaseDateGe;
  private LocalDate expirationDateGe;
}

