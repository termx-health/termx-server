package com.kodality.termserver.commons.model.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QueryParams {
  private List<String> sort = new ArrayList<>();
  private Integer limit = 101;
  private Integer offset = 0;

  public void all() {
    setLimit(-1);
  }
}
