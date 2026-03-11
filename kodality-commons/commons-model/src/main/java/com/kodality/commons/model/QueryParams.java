package com.kodality.commons.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

@Getter
@Setter
public class QueryParams {
  private List<String> sort = new ArrayList<>();
  private Integer limit = 101;
  private Integer offset = 0;

  public <T extends QueryParams> T sort(List<String> sort) {
    this.sort = sort;
    return (T) this;
  }

  public <T extends QueryParams> T limit(Integer limit) {
    this.limit = limit;
    return (T) this;
  }

  public <T extends QueryParams> T offset(Integer offset) {
    this.offset = offset;
    return (T) this;
  }

  public <T extends QueryParams> T all() {
    return limit(-1);
  }

  /**
   * @param param comma-separated list
   * @return comma-separated list, filtered by 'allowed'
   */
  public static String intersection(String param, Collection<String> allowed) {
    return String.join(",", StringUtils.isBlank(param) ? allowed :
        Arrays.stream(param.split(",")).filter(allowed::contains).toList()
    );
  }
}
