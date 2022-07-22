package com.kodality.termserver.snomed;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class SnomedSearchResult<T> {
  private List<T> items;
  private Integer total;
  private Integer limit;
  private Integer offset;
}
