package com.kodality.termserver.common.utils;

import io.github.furstenheim.CopyDown;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

public class TextUtil {

  public static String convertText(String text, String fromFormat, String toFormat) {
    if ("html".equals(fromFormat) && "markdown".equals(toFormat)) {
      return new CopyDown().convert(text);
    }
    if ("markdown".equals(fromFormat) && "html".equals(toFormat)) {
      return HtmlRenderer.builder().build().render(Parser.builder().build().parse(text));
    }
    return text;
  }
}
