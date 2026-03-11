package com.kodality.zmei.fhir.search;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FhirQueryParams extends LinkedHashMap<String, List<String>> {
  public static final String count = "_count";
  public static final String page = "_page";
  public static final String sort = "_sort";

  public FhirQueryParams() {
    //
  }

  public FhirQueryParams(Map<String, List<String>> query) {
    putAll(query);
  }

  public static FhirQueryParams of(String... map) {
    FhirQueryParams p = new FhirQueryParams();
    for (int i = 0; i < map.length; i = i + 2) {
      p.computeIfAbsent(map[i], k -> new ArrayList<>(1)).add(map[i + 1]);
    }
    return p;
  }

  public List<String> putSingle(String key, String param) {
    if (param == null) {
      return remove(key);
    } else {
      return put(key, List.of(param));
    }
  }

  public Optional<String> getFirst(String key) {
    return containsKey(key) ? get(key).stream().findFirst() : Optional.empty();
  }

  public Integer getCount() {
    return getFirst(count).map(Integer::valueOf).orElse(10);
  }

  public Integer getPage() {
    return getFirst(page).map(Integer::valueOf).orElse(1);
  }

  public Integer getOffset() {
    return (getPage() - 1) * getCount();
  }

  public void setOffset(Integer offset) {
    if (getCount() == null) {
      throw new IllegalStateException("cannot set offset without count");
    }
    int page = getCount() == 0 ? 0 : (offset / getCount()) + 1;
    put(FhirQueryParams.page, List.of(Integer.toString(page)));
  }

  public List<String> getSort() {
    return get(sort);
  }

}
