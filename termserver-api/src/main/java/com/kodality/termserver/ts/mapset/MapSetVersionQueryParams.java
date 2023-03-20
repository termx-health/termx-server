package com.kodality.termserver.ts.mapset;

import com.kodality.commons.model.QueryParams;
import java.time.LocalDate;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class MapSetVersionQueryParams extends QueryParams {
  private String mapSet;
  private List<String> permittedMapSets;
  private String version;
  private String status;
  private LocalDate releaseDateLe;
  private LocalDate expirationDateGe;
}
