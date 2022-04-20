package com.kodality.termserver.commons.model.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QueryResult<T> {
  private List<T> data;
  private SearchResultMeta meta = new SearchResultMeta();
  @JsonIgnore
  private QueryParams queryParams;

  public QueryResult() {
  }

  public QueryResult(List<T> data) {
    this.data = data;
    this.meta.setTotal(data.size());
  }

  public static <T> QueryResult<T> empty() {
    return new QueryResult<T>(List.of());
  }

  public <V> QueryResult<V> map(Function<T, V> mapper) {
    QueryResult<V> result = new QueryResult<>();
    result.queryParams = queryParams;
    result.meta = meta;
    if (data != null) {
      result.data = data.stream()
          .map(mapper)
          .collect(Collectors.toList());
    }
    return result;
  }

  public Optional<T> findFirst() {
    return meta == null || meta.getTotal() == null || meta.getTotal() == 0 ? Optional.empty() : Optional.of(data.get(0));
  }

  public QueryResult(Integer total, QueryParams params) {
    this.queryParams = params;
    this.meta.setTotal(total);
    this.meta.setPages(params.getLimit() == 0 ? 0 : (int) Math.ceil((double) total / params.getLimit()));
    this.meta.setItemsPerPage(params.getLimit());
    this.meta.setOffset(params.getOffset());
  }

  @Getter
  @Setter
  public static class SearchResultMeta {
    private Integer total;
    private Integer pages;
    private Integer offset;
    private Integer itemsPerPage;
  }

}
