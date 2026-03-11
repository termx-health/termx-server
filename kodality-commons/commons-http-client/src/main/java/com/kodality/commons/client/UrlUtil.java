package com.kodality.commons.client;

import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;

@UtilityClass
public class UrlUtil {

  public String url(String... parts) {
    if (parts == null) {
      return "";
    }
    return Stream.of(parts).filter(a -> a != null).map(UrlUtil::removeSlashes).collect(joining("/"));
  }

  private String removeSlashes(String str) {
    String result = str;
    result = StringUtils.removeStart(result, "/");
    result = StringUtils.removeEnd(result, "/");
    return result;
  }
}
