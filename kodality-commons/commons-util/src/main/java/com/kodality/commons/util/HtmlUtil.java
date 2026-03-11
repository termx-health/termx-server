package com.kodality.commons.util;

import java.util.Arrays;
import java.util.stream.Collectors;
import org.jsoup.Jsoup;

public class HtmlUtil {

  public static String toText(String html) {
    if (html == null) {
      return null;
    }
    return Arrays.stream(html.split("<br>")).map(h -> Jsoup.parse(h).text()).collect(Collectors.joining("\n"));
  }
}
