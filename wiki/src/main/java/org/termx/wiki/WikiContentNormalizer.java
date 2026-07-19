package org.termx.wiki;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites TermX Wiki markdown into a form that renders identically in the wiki (markdown-it)
 * and in mdbook (VitePress/Vue). Pure, dependency-free functions only, so it is safe to call
 * from a Liquibase customChange during startup as well as from application code.
 *
 * <p>Rules are documented in {@code docs/wiki-mdbook-syntax.md} and mirror mdbook's build-time
 * fallback in {@code src/ingest/sanitize.mjs}.
 */
public final class WikiContentNormalizer {
  private WikiContentNormalizer() {}

  /** R1: the Wiki.js autolink-breaker — a bare, attribute-less opening {@code <span>}
   * (optionally an empty {@code <span></span>} pair). Spans carrying attributes are
   * meaningful, and a standalone closing {@code </span>} belongs to one, so neither is
   * touched. */
  private static final Pattern BARE_SPAN =
      Pattern.compile("<span\\s*>(?:\\s*</span\\s*>)?", Pattern.CASE_INSENSITIVE);

  /** R2: a fenced-code opening line — optional indent, {@code ```}, a language id, optional
   * trailing whitespace. Closing fences carry no language, so they never match. */
  private static final Pattern FENCE = Pattern.compile("(?m)^(\\s*```)([A-Za-z0-9_+-]+)([ \\t]*)$");

  /** Stray fence languages Shiki doesn't know, mapped to real ids (an unknown language
   * hard-fails the VitePress build). Extend as the content audit surfaces more. */
  private static final Map<String, String> FENCE_ALIAS = Map.of("s", "sh");

  /** Returns {@code content} rewritten to the both-compatible form, or the input unchanged
   * (including null/empty) when no rule applies. */
  public static String normalize(String content) {
    if (content == null || content.isEmpty()) {
      return content;
    }
    String out = content;
    out = BARE_SPAN.matcher(out).replaceAll("<!-- -->");
    out = aliasFenceLanguages(out);
    return out;
  }

  /** True when {@link #normalize} would change the given content. */
  public static boolean needsNormalization(String content) {
    return content != null && !content.equals(normalize(content));
  }

  private static String aliasFenceLanguages(String text) {
    Matcher m = FENCE.matcher(text);
    StringBuilder sb = new StringBuilder();
    while (m.find()) {
      String alias = FENCE_ALIAS.get(m.group(2));
      String replacement = alias == null ? m.group() : m.group(1) + alias + m.group(3);
      m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
    }
    m.appendTail(sb);
    return sb.toString();
  }
}
