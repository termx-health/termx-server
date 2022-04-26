package com.kodality.termserver.codesystem;

import com.kodality.commons.model.QueryParams;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class CodeSystemVersionQueryParams extends QueryParams {
  private String codeSystem;
  private String version;
  private String status;
  private LocalDate releaseDateLe;
  private LocalDate expirationDateGe;
}
