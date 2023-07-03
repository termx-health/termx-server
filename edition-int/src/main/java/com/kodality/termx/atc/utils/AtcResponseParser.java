package com.kodality.termx.atc.utils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class AtcResponseParser {

  public static Map<String, String> parse(String htmlResponse) {
    int beginIndex = htmlResponse.indexOf("<table>");
    String substring = htmlResponse.substring(beginIndex + 7, htmlResponse.indexOf("</table>"));
    Pattern pattern = Pattern.compile("<a href=\"\\./\\?code=(.*?)\">(.*?)</a>");
    Matcher matcher = pattern.matcher(substring);
    Map<String, String> codeNames = new LinkedHashMap<>();
    while (matcher.find()) {
      String code = matcher.group(1);
      String value = matcher.group(2);
      if (StringUtils.isEmpty(value)) {
        log.warn("ATC code '{}' has empty value", code);
        continue;
      }
      if (codeNames.containsKey(code) && StringUtils.equals(codeNames.get(code), value)) {
        log.trace("Duplicate value for ATC code '{}' found, ignoring", code);
      }
      codeNames.compute(code, (k, v) -> StringUtils.firstNonEmpty(v, value));
    }
    return codeNames;
  }

}
