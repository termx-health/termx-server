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

  // GitBook hint styles -> TermX callout classes.
  private static final Map<String, String> HINT_CLASS = Map.of(
      "info", "is-info", "warning", "is-warning", "danger", "is-error", "success", "is-success");

  public static String convert(String body) {
    if (body == null) {
      return null;
    }
    String out = FRONTMATTER.matcher(body).replaceFirst("");
    out = convertCardTables(out);
    out = convertFileEmbeds(out);
    out = convertHints(out);
    return out;
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
      m.appendReplacement(sb, Matcher.quoteReplacement("[" + name + "](" + src + ")"));
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
