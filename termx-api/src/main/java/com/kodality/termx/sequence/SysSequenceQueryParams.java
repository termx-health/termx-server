package com.kodality.termx.sequence;

import com.kodality.commons.model.QueryParams;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class SysSequenceQueryParams extends QueryParams {
  private String textContains;
  private String codes;

  public interface Ordering {
    String code = "code";
  }
}
