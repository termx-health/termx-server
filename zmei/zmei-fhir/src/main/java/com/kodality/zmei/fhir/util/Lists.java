package com.kodality.zmei.fhir.util;

import java.util.ArrayList;
import java.util.List;

public final class Lists {

  private Lists() {
  }

  public static <T> List<T> add(List<T> ts, T t) {
    if (ts == null) {
      ts = new ArrayList<>();
    }
    if (t != null) {
      ts.add(t);
    }
    return ts;
  }
}
