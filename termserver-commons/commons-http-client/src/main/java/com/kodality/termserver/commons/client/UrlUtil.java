package com.kodality.termserver.commons.client;

import java.util.Objects;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

import static java.util.stream.Collectors.joining;

@UtilityClass
public class UrlUtil {

  public String url(String... parts) {
    if (parts == null) {
      return "";
    }
    return Stream.of(parts).filter(Objects::nonNull).map(UrlUtil::removeSlashes).collect(joining("/"));
  }

  private String removeSlashes(String str) {
    String result = str;
    result = StringUtils.removeStart(result, "/");
    result = StringUtils.removeEnd(result, "/");
    return result;
  }
}
