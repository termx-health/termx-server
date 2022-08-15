package com.kodality.termserver.valueset;

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
  private List<String> permittedValueSets;
  private String valueSetUri;
  private String version;
  private String status;
  private LocalDate releaseDateLe;
  private LocalDate expirationDateGe;
  private boolean decorated;
}
