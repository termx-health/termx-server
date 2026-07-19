package org.termx.wiki.importer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

/**
 * Normalizes GitBook-specific markdown into a form the TermX wiki renderer displays correctly —
 * the counterpart to mdbook's build-time GitBook transforms. Applied to each imported GitBook page:
 * <ul>
 *   <li>drops the leading YAML frontmatter block;</li>
 *   <li>rewrites {@code <table data-view="cards">} card tables to plain tables, removing the
 *       {@code data-hidden} columns (which otherwise render as stray {@code null} cells);</li>
 *   <li>turns {@code {% file src="…" %}} into a link and {@code {% hint style="…" %}…{% endhint %}}
 *       into a TermX callout blockquote.</li>
 * </ul>
 */
public final class GitbookConverter {
  private GitbookConverter() {}

  private static final Pattern FRONTMATTER = Pattern.compile("^\\uFEFF?---\\r?\\n.*?\\r?\\n---\\r?\\n", Pattern.DOTALL);
  private static final Pattern CARD_TABLE = Pattern.compile("(?is)<table\\b[^>]*\\bdata-view=\"cards\"[^>]*>.*?</table>");
  private static final Pattern FILE_EMBED = Pattern.compile("\\{%\\s*file\\s+src=\"([^\"]+)\"[^%]*%\\}");
  private static final Pattern HINT = Pattern.compile("(?is)\\{%\\s*hint\\s+style=\"([^\"]+)\"\\s*%\\}(.*?)\\{%\\s*endhint\\s*%\\}");
  private static final Pattern IMG_TAG = Pattern.compile("(?is)<img\\b[^>]*?/?>");
  private static final Pattern IMG_SRC = Pattern.compile("(?i)\\bsrc=[\"']([^\"']*)[\"']");
  private static final Pattern IMG_ALT = Pattern.compile("(?i)\\balt=[\"']([^\"']*)[\"']");
  // GitBook wraps images as <div align><figure><img><figcaption></figure></div>; markdown inside
  // an HTML block isn't processed, so unwrap the whole block to a standalone markdown image.
  private static final Pattern FIGURE_BLOCK =
      Pattern.compile("(?is)(?:<div\\b[^>]*>\\s*)?<figure\\b[^>]*>\\s*(<img\\b[^>]*>).*?</figure>(?:\\s*</div>)?");

  // GitBook hint styles -> TermX callout classes.
  private static final Map<String, String> HINT_CLASS = Map.of(
      "info", "is-info", "warning", "is-warning", "danger", "is-error", "success", "is-success");

  public static String convert(String body) {
    if (body == null) {
      return null;
    }
    String out = FRONTMATTER.matcher(body).replaceFirst("");
    out = convertCardTables(out);
    out = convertFigures(out);
    out = convertImages(out);
    out = convertFileEmbeds(out);
    out = convertHints(out);
    return out;
  }

  private static String convertFigures(String body) {
    Matcher m = FIGURE_BLOCK.matcher(body);
    StringBuilder sb = new StringBuilder();
    while (m.find()) {
      m.appendReplacement(sb, Matcher.quoteReplacement(imgToMarkdown(m.group(1), m.group())));
    }
    m.appendTail(sb);
    return sb.toString();
  }

  /** Raw {@code <img>} tags aren't rewritten by the wiki's attachment renderer (only markdown
   * images are), so turn them into markdown so imported images resolve. */
  private static String convertImages(String body) {
    Matcher m = IMG_TAG.matcher(body);
    StringBuilder sb = new StringBuilder();
    while (m.find()) {
      m.appendReplacement(sb, Matcher.quoteReplacement(imgToMarkdown(m.group(), m.group())));
    }
    m.appendTail(sb);
    return sb.toString();
  }

  /** A single {@code <img>} tag as a markdown image (URL angle-bracketed so spaces stay valid), or
   * {@code fallback} when it has no src. */
  private static String imgToMarkdown(String imgTag, String fallback) {
    Matcher src = IMG_SRC.matcher(imgTag);
    if (!src.find()) {
      return fallback;
    }
    Matcher alt = IMG_ALT.matcher(imgTag);
    return "![" + (alt.find() ? alt.group(1) : "") + "](<" + src.group(1) + ">)";
  }

  private static String convertCardTables(String body) {
    Matcher m = CARD_TABLE.matcher(body);
    StringBuilder sb = new StringBuilder();
    while (m.find()) {
      m.appendReplacement(sb, Matcher.quoteReplacement(cleanCardTable(m.group())));
    }
    m.appendTail(sb);
    return sb.toString();
  }

  private static String cleanCardTable(String tableHtml) {
    Document doc = Jsoup.parse(tableHtml, "", Parser.xmlParser());
    Element table = doc.selectFirst("table");
    if (table == null) {
      return tableHtml;
    }
    table.removeAttr("data-view");

    Elements headerCells = table.select("thead > tr > th");
    List<Integer> hidden = new ArrayList<>();
    for (int i = 0; i < headerCells.size(); i++) {
      if (headerCells.get(i).hasAttr("data-hidden")) {
        hidden.add(i);
      }
    }
    // Remove hidden columns from every row (highest index first so positions stay valid).
    for (Element row : table.select("tr")) {
      Elements cells = row.children();
      for (int j = hidden.size() - 1; j >= 0; j--) {
        int idx = hidden.get(j);
        if (idx < cells.size()) {
          cells.get(idx).remove();
        }
      }
    }
    // GitBook card tables have no header text — drop an all-empty header row.
    Element thead = table.selectFirst("thead");
    if (thead != null && thead.select("th").stream().allMatch(th -> th.text().isBlank())) {
      thead.remove();
    }
    return table.outerHtml();
  }

  private static String convertFileEmbeds(String body) {
    Matcher m = FILE_EMBED.matcher(body);
    StringBuilder sb = new StringBuilder();
    while (m.find()) {
      String src = m.group(1);
      String name = StringUtils.substringAfterLast(src, "/");
      if (name.isEmpty()) {
        name = src;
      }
      m.appendReplacement(sb, Matcher.quoteReplacement("[" + name + "](<" + src + ">)"));
    }
    m.appendTail(sb);
    return sb.toString();
  }

  private static String convertHints(String body) {
    Matcher m = HINT.matcher(body);
    StringBuilder sb = new StringBuilder();
    while (m.find()) {
      String cls = HINT_CLASS.getOrDefault(m.group(1), "is-info");
      String text = m.group(2).trim();
      String quoted = text.lines().map(l -> "> " + l).reduce((a, b) -> a + "\n" + b).orElse("> ");
      m.appendReplacement(sb, Matcher.quoteReplacement(quoted + "\n{." + cls + "}"));
    }
    m.appendTail(sb);
    return sb.toString();
  }
}
