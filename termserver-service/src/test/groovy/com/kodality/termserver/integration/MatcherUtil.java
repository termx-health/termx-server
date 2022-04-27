package com.kodality.termserver.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MatcherUtil {
  public static List<String> findAllMatches(String object, String regex) {
    List<String> allMatches = new ArrayList<>();
    Matcher m = Pattern.compile(regex)
        .matcher(object);
    while (m.find()) {
      allMatches.add(m.group());
    }
    return allMatches;
  }
}
