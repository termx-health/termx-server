package com.kodality.termserver.commons.db.util;

import java.util.Collection;
import static java.util.stream.Collectors.joining;

public final class PgUtil {

  public static String array(Collection<?> list) {
    if (list == null) {
      return null;
    }
    return "{" + list.stream().map(Object::toString).collect(joining(",")) + "}";
  }

}
