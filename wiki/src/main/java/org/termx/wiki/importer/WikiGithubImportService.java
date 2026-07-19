package org.termx.wiki.importer;

import com.github.slugify.Slugify;
import com.kodality.commons.micronaut.rest.MultipartBodyReader.Attachment;
import com.kodality.commons.util.JsonUtil;
import org.termx.core.sys.space.SpaceService;
import org.termx.sys.space.Space;
import org.termx.wiki.SpaceGithubDataWikiHandler;
import org.termx.wiki.page.Page;
import org.termx.wiki.page.PageContent;
import org.termx.wiki.page.PageQueryParams;
import org.termx.wiki.page.PageService;
import org.termx.wiki.pageattachment.PageAttachmentService;
import org.termx.wiki.pagecontent.PageContentService;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.io.IOUtils;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.transaction.annotation.Transactional;

/**
 * Imports a wiki Space's content straight from a GitHub repository, without configuring the
 * Space's git integration or performing any git/OAuth operations. Fetches the export files over
 * plain HTTP (the tree API + raw.githubusercontent, unauthenticated for public repos, or with an
 * optional token for private ones) and feeds them to the existing round-trip parser
 * {@link SpaceGithubDataWikiHandler#saveContent}.
 *
 * <p>Both export layouts are accepted: the round-trip {@code wiki} format
 * ({@code <dir>/pages.json} + {@code <dir>/<slug>.md}) and the {@code wiki-ssg} format
 * ({@code <dir>/pages.json} + {@code <dir>/pages/<slug>.md}, whose page-content field is
 * {@code contentType} rather than {@code ct}). The content directory is auto-detected from where
 * {@code pages.json} lives when not given.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor
public class WikiGithubImportService {
  private static final String GITHUB_API = "https://api.github.com";
  private static final String GITHUB_RAW = "https://raw.githubusercontent.com";

  private final SpaceGithubDataWikiHandler wikiHandler;
  private final SpaceService spaceService;
  private final PageService pageService;
  private final PageContentService pageContentService;
  private final PageAttachmentService pageAttachmentService;

  private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

  @Transactional
  public WikiGithubImportResult importSpace(WikiGithubImportRequest req) {
    GithubCoords coords = GithubCoords.resolve(req);
    Space space = spaceService.load(req.getSpaceId());
    if (space == null) {
      throw new RuntimeException("space not found: " + req.getSpaceId());
    }

    String branch = StringUtils.isNotBlank(coords.branch()) ? coords.branch() : defaultBranch(coords, req.getToken());
    List<String> blobs = listBlobs(coords, branch, req.getToken());

    String dir = StringUtils.isNotBlank(coords.dir())
        ? StringUtils.strip(coords.dir(), "/")
        : autodetectDir(blobs);
    String prefix = dir.isEmpty() ? "" : dir + "/";

    // TermX (pages.json) vs GitBook (SUMMARY.md). pages.json wins if both exist.
    boolean termx = blobs.contains(prefix + "pages.json");
    boolean gitbook = !termx && blobs.contains(prefix + "SUMMARY.md");

    // saveContent mutates the map, so it must be mutable.
    Map<String, String> content = new LinkedHashMap<>();
    Map<String, String> hrefDirByCode = new HashMap<>(); // GitBook: page code -> its .md directory
    int pages = gitbook
        ? buildFromGitbook(content, coords, branch, prefix, req, hrefDirByCode)
        : buildFromTermx(content, coords, branch, dir, prefix, blobs, req);

    log.info("importing {} page(s) into space {} from {}/{}@{} ({}, {})",
        pages, req.getSpaceId(), coords.owner(), coords.repo(), branch, dir.isEmpty() ? "/" : dir,
        gitbook ? "gitbook" : "termx");
    wikiHandler.saveContent(req.getSpaceId(), content);

    int attachments = importAttachments(req.getSpaceId(), coords, branch, prefix, req.getToken(), gitbook, hrefDirByCode);

    return new WikiGithubImportResult(coords.owner() + "/" + coords.repo(), branch, dir, pages, attachments);
  }

  // ── TermX (pages.json + <slug>.md / pages/<slug>.md) ───────────────────────

  private int buildFromTermx(Map<String, String> content, GithubCoords coords, String branch, String dir,
                             String prefix, List<String> blobs, WikiGithubImportRequest req) {
    String pagesJson = fetchRaw(coords, branch, prefix + "pages.json", req.getToken());
    if (pagesJson == null) {
      throw new RuntimeException("pages.json not found at '" + prefix + "pages.json' in "
          + coords.owner() + "/" + coords.repo() + "@" + branch);
    }
    // Normalize pages.json to the round-trip shape and learn the slug -> name of every content.
    Map<String, String> slugToName = new HashMap<>();
    Object tree = JsonUtil.fromJson(pagesJson, Object.class);
    normalizeNodes(tree, slugToName);
    content.put("pages.json", JsonUtil.toJson(tree));

    // The .md is keyed by its own slug — the same slug pages.json declares, which the parser now
    // stores verbatim (slugs are stable, PageContentService.prepare no longer re-derives them from
    // the name). Only .md referenced by pages.json is fetched (orphans are skipped so the parser
    // can't NPE); attachments/resources are not imported.
    Map<String, String> keyToPath = new LinkedHashMap<>();
    for (String path : blobs) {
      if (!path.endsWith(".md") || !path.startsWith(prefix)) {
        continue;
      }
      String fileSlug = StringUtils.removeEnd(StringUtils.substringAfterLast("/" + path, "/"), ".md");
      if (slugToName.containsKey(fileSlug)) {
        keyToPath.put(fileSlug + ".md", path);
      }
    }
    Map<String, String> fetched = fetchAll(keyToPath, coords, branch, req.getToken(), false);
    content.putAll(fetched);
    return fetched.size();
  }

  // ── GitBook (SUMMARY.md tree + linked .md) ─────────────────────────────────

  private int buildFromGitbook(Map<String, String> content, GithubCoords coords, String branch,
                               String prefix, WikiGithubImportRequest req, Map<String, String> hrefDirByCode) {
    String summary = fetchRaw(coords, branch, prefix + "SUMMARY.md", req.getToken());
    if (summary == null) {
      throw new RuntimeException("SUMMARY.md not found at '" + prefix + "SUMMARY.md'");
    }
    List<GbNode> roots = parseSummary(summary);
    String lang = StringUtils.isNotBlank(req.getLang()) ? req.getLang() : "en";
    Slugify slugify = Slugify.builder().build();

    List<GbNode> all = new ArrayList<>();
    flatten(roots, all);
    // Fetch the linked markdown concurrently (keyed by the same slug the parser will re-derive).
    Map<String, String> keyToPath = new LinkedHashMap<>();
    for (GbNode n : all) {
      if (n.href != null && n.href.endsWith(".md") && !n.href.matches("^https?://.*")) {
        keyToPath.put(slugify.slugify(n.title) + ".md", prefix + n.href);
      }
    }
    Map<String, String> bodies = fetchAll(keyToPath, coords, branch, req.getToken(), true);

    List<Map<String, Object>> pagesJson = new ArrayList<>();
    buildGbStructure(roots, pagesJson, content, bodies, lang, slugify, hrefDirByCode);
    content.put("pages.json", JsonUtil.toJson(pagesJson));
    return all.size();
  }

  private void flatten(List<GbNode> nodes, List<GbNode> out) {
    for (GbNode n : nodes) {
      out.add(n);
      flatten(n.children, out);
    }
  }

  /** Builds round-trip pages.json nodes + keyed .md bodies from the parsed SUMMARY tree. Codes are a
   * deterministic hash of the href/section, so re-importing updates rather than recreating; `##`
   * sections (and pages whose file is missing) get a title-only body. */
  private void buildGbStructure(List<GbNode> nodes, List<Map<String, Object>> out, Map<String, String> content,
                                Map<String, String> bodies, String lang, Slugify slugify, Map<String, String> hrefDirByCode) {
    for (GbNode n : nodes) {
      String slug = slugify.slugify(n.title);
      String key = n.href != null ? "gitbook:" + n.href : "gitbook-section:" + n.title;
      String code = UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
      if (n.href != null && n.href.contains("/")) {
        hrefDirByCode.put(code, StringUtils.substringBeforeLast(n.href, "/")); // for resolving relative assets
      }

      Map<String, Object> contentObj = new LinkedHashMap<>();
      contentObj.put("name", n.title);
      contentObj.put("slug", slug);
      contentObj.put("lang", lang);
      contentObj.put("ct", "markdown");

      List<Map<String, Object>> children = new ArrayList<>();
      buildGbStructure(n.children, children, content, bodies, lang, slugify, hrefDirByCode);

      Map<String, Object> node = new LinkedHashMap<>();
      node.put("code", code);
      node.put("contents", List.of(contentObj));
      node.put("children", children.isEmpty() ? null : children);
      out.add(node);

      String body = bodies.get(slug + ".md");
      content.put(slug + ".md", body != null ? body : "# " + n.title + "\n");
    }
  }

  /** Fetches many raw files concurrently (bounded pool), returning key -> body for those that
   * exist (404s are skipped). When `gitbook` is set, each body is normalized for the TermX wiki
   * renderer (frontmatter, card tables, file/hint blocks) via {@link GitbookConverter}. */
  private Map<String, String> fetchAll(Map<String, String> keyToPath, GithubCoords coords, String branch,
                                       String token, boolean gitbook) {
    Map<String, String> out = new ConcurrentHashMap<>();
    if (keyToPath.isEmpty()) {
      return out;
    }
    ExecutorService pool = Executors.newFixedThreadPool(Math.min(8, keyToPath.size()));
    try {
      List<Future<?>> futures = new ArrayList<>();
      keyToPath.forEach((key, path) -> futures.add(pool.submit(() -> {
        String body = fetchRaw(coords, branch, path, token);
        if (gitbook) {
          body = GitbookConverter.convert(body);
        }
        if (body != null) {
          out.put(key, body);
        }
      })));
      for (Future<?> f : futures) {
        try {
          f.get();
        } catch (Exception e) {
          throw new RuntimeException("failed fetching content from GitHub", e);
        }
      }
    } finally {
      pool.shutdown();
    }
    return out;
  }

  // ── attachments ────────────────────────────────────────────────────────────

  // Markdown image / link, with an optional <bracketed> URL (so asset names with spaces parse).
  private static final Pattern MD_IMAGE = Pattern.compile("!\\[([^\\]]*)]\\(\\s*(?:<([^>]*)>|([^)\\s]+))\\s*(?:\"[^\"]*\")?\\)");
  private static final Pattern MD_LINK = Pattern.compile("(?<!!)\\[([^\\]]*)]\\(\\s*(?:<([^>]*)>|([^)\\s]+))\\s*(?:\"[^\"]*\")?\\)");
  // A TermX attachment reference: files/<folder>/<name>. The folder is usually the numeric page id,
  // but legacy content also uses a named folder (e.g. files/wiki/x.png stored under attachments/wiki/).
  private static final Pattern FILES_REF = Pattern.compile("files/([^/]+)/(.+)");
  private static final Map<String, String> CONTENT_TYPES = Map.of(
      "png", "image/png", "jpg", "image/jpeg", "jpeg", "image/jpeg", "gif", "image/gif",
      "svg", "image/svg+xml", "webp", "image/webp", "pdf", "application/pdf");

  /** After the pages exist, fetch each page's referenced images/files, store them as page
   * attachments and rewrite the references to {@code ![alt](files/<pageId>/<name>)}. A content hash
   * lets a re-import skip files that are already attached unchanged. */
  private int importAttachments(Long spaceId, GithubCoords coords, String branch, String prefix, String token,
                                boolean gitbook, Map<String, String> hrefDirByCode) {
    int[] stored = {0};
    List<Page> pages = pageService.query(new PageQueryParams().setSpaceIds(spaceId.toString()).all()).getData();
    for (Page page : pages) {
      String hrefDir = gitbook ? hrefDirByCode.getOrDefault(page.getCode(), "") : "";
      for (PageContent pc : pageContentService.loadAll(page.getId())) {
        String body = pc.getContent();
        if (body == null || body.isEmpty()) {
          continue;
        }
        Map<String, String> cache = new HashMap<>(); // repoPath -> stored (sanitized) name, fetched once
        String updated = rewriteAssets(body, MD_IMAGE, true, page.getId(), prefix, gitbook, hrefDir, coords, branch, token, cache, stored);
        updated = rewriteAssets(updated, MD_LINK, false, page.getId(), prefix, gitbook, hrefDir, coords, branch, token, cache, stored);
        if (!updated.equals(body)) {
          pc.setContent(updated);
          pageContentService.save(pc, page.getId());
        }
      }
    }
    return stored[0];
  }

  private String rewriteAssets(String body, Pattern pattern, boolean image, Long pageId, String prefix,
                               boolean gitbook, String hrefDir, GithubCoords coords, String branch, String token,
                               Map<String, String> cache, int[] stored) {
    Matcher m = pattern.matcher(body);
    StringBuilder sb = new StringBuilder();
    while (m.find()) {
      String label = m.group(1);
      // Decode %XX so a percent-encoded name (files/207/Screenshot%202024...png) resolves to the
      // real repo path and gets a clean stored name; the original text is kept if we don't import it.
      String url = decodeRef(m.group(2) != null ? m.group(2).trim() : m.group(3));
      String replacement = m.group(); // keep the original unless we import the asset
      if (importableAsset(url, image, pageId)) {
        try {
          String repoPath = resolveRepoPath(url, prefix, gitbook, hrefDir);
          String name = cache.get(repoPath);
          if (name == null && !cache.containsKey(repoPath)) {
            byte[] bytes = fetchBytes(coords, branch, repoPath, token);
            name = bytes == null ? null : safeFileName(attachmentName(url));
            if (bytes != null) {
              storeAttachment(pageId, name, bytes);
            }
            cache.put(repoPath, name);
          }
          if (name != null) {
            replacement = (image ? "!" : "") + "[" + label + "](files/" + pageId + "/" + name + ")";
            stored[0]++;
          }
        } catch (Exception e) {
          log.warn("skipping attachment '{}' on page {}: {}", url, pageId, e.getMessage());
        }
      }
      m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
    }
    m.appendTail(sb);
    return sb.toString();
  }

  static boolean importableAsset(String url, boolean image, Long thisPageId) {
    if (url == null || url.isEmpty() || url.matches("(?i)^(https?:|mailto:|tel:|data:|#).*")
        || url.startsWith("files/" + thisPageId + "/")) {
      return false;
    }
    return image // all local images are assets; links only when they clearly point at one
        || url.contains(".gitbook/assets/") || url.startsWith("attachments/") || FILES_REF.matcher(url).matches();
  }

  /** A file name safe for a wiki attachment reference (no spaces or special characters). */
  static String safeFileName(String name) {
    String s = name.replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("-+\\.", ".").replaceAll("-{2,}", "-").replaceAll("^-|-$", "");
    return s.isEmpty() ? "file" : s;
  }

  static String attachmentName(String ref) {
    String name = ref.contains("/") ? StringUtils.substringAfterLast(ref, "/") : ref;
    return StringUtils.substringBefore(StringUtils.substringBefore(name, "?"), "#");
  }

  /** Decodes %XX escapes in a reference (keeping a literal {@code +}); returns it unchanged if it
   * holds no escapes or is malformed. */
  static String decodeRef(String ref) {
    if (ref == null || ref.indexOf('%') < 0) {
      return ref;
    }
    try {
      return URLDecoder.decode(ref.replace("+", "%2B"), StandardCharsets.UTF_8);
    } catch (RuntimeException e) {
      return ref;
    }
  }

  static String resolveRepoPath(String ref, String prefix, boolean gitbook, String hrefDir) {
    Matcher fm = FILES_REF.matcher(ref);
    if (fm.matches()) {
      return prefix + "attachments/" + fm.group(1) + "/" + fm.group(2); // TermX export layout
    }
    if (ref.contains(".gitbook/assets/")) {
      return prefix + ".gitbook/assets/" + attachmentName(ref); // GitBook assets live at the book root
    }
    String base = gitbook && StringUtils.isNotBlank(hrefDir) ? hrefDir + "/" : "";
    return normalizePath(prefix + base + ref);
  }

  /** Resolves {@code ./} and {@code ../} segments in a repo path. */
  static String normalizePath(String path) {
    Deque<String> stack = new ArrayDeque<>();
    for (String seg : path.split("/")) {
      if (seg.isEmpty() || seg.equals(".")) {
        continue;
      }
      if (seg.equals("..")) {
        if (!stack.isEmpty()) {
          stack.removeLast();
        }
      } else {
        stack.addLast(seg);
      }
    }
    return String.join("/", stack);
  }

  /** Stores bytes as a page attachment, skipping the upload when an identical file (same name and
   * content hash) is already attached; replaces it when the name matches but the content differs. */
  void storeAttachment(Long pageId, String fileName, byte[] bytes) {
    String hash = sha256(bytes);
    boolean present = pageAttachmentService.getAttachments(pageId).stream()
        .anyMatch(a -> fileName.equals(a.getFileName()));
    if (present) {
      try {
        byte[] cur = IOUtils.toByteArray(pageAttachmentService.getAttachmentContent(pageId, fileName).getInputStream());
        if (sha256(cur).equals(hash)) {
          return; // identical — reuse the existing attachment
        }
      } catch (Exception ignore) {
        // couldn't read the existing one; fall through and replace
      }
      pageAttachmentService.deleteAttachmentContent(pageId, fileName);
    }
    Attachment a = new Attachment()
        .setFileName(fileName)
        .setContentType(contentTypeOf(fileName))
        .setContent(bytes)
        .setContentLength((long) bytes.length);
    pageAttachmentService.saveAttachments(pageId, Map.of(fileName, a));
  }

  private byte[] fetchBytes(GithubCoords c, String branch, String path, String token) {
    HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(rawUrl(c, branch, path))).GET()
        .header("User-Agent", "termx-wiki-import");
    if (StringUtils.isNotBlank(token)) {
      b.header("Authorization", "Bearer " + token);
    }
    try {
      HttpResponse<byte[]> resp = http.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
      if (resp.statusCode() == 404) {
        return null;
      }
      if (resp.statusCode() >= 400) {
        throw new RuntimeException("HTTP " + resp.statusCode());
      }
      return resp.body();
    } catch (Exception e) {
      throw new RuntimeException("failed to fetch " + path, e);
    }
  }

  static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static String contentTypeOf(String fileName) {
    String ext = fileName.contains(".") ? StringUtils.substringAfterLast(fileName, ".").toLowerCase() : "";
    return CONTENT_TYPES.getOrDefault(ext, "application/octet-stream");
  }

  private static final Pattern GB_SECTION = Pattern.compile("^#{2,}\\s+(.+?)\\s*$");
  private static final Pattern GB_BULLET = Pattern.compile("^(\\s*)[-*+]\\s+\\[([^\\]]+)]\\(([^)]+)\\)");

  /** Parses a GitBook SUMMARY.md into a nested node tree. `## Heading` starts a top-level section
   * (a group page); bullet links nest by indentation. */
  private List<GbNode> parseSummary(String summary) {
    List<GbNode> roots = new ArrayList<>();
    GbNode section = null;
    List<GbNode> stack = new ArrayList<>();      // bullet-nesting stack (by indent)
    List<Integer> indents = new ArrayList<>();
    for (String raw : summary.split("\n")) {
      Matcher sec = GB_SECTION.matcher(raw);
      if (sec.matches()) {
        section = new GbNode(sec.group(1).trim(), null);
        roots.add(section);
        stack.clear();
        indents.clear();
        continue;
      }
      Matcher bul = GB_BULLET.matcher(raw);
      if (bul.find()) {
        int indent = bul.group(1).length();
        GbNode node = new GbNode(bul.group(2).trim(), bul.group(3).trim());
        while (!indents.isEmpty() && indents.get(indents.size() - 1) >= indent) {
          indents.remove(indents.size() - 1);
          stack.remove(stack.size() - 1);
        }
        if (stack.isEmpty()) {
          (section != null ? section.children : roots).add(node);
        } else {
          stack.get(stack.size() - 1).children.add(node);
        }
        stack.add(node);
        indents.add(indent);
      }
    }
    return roots;
  }

  private static final class GbNode {
    final String title;
    final String href; // null for a `## Section` group
    final List<GbNode> children = new ArrayList<>();
    GbNode(String title, String href) { this.title = title; this.href = href; }
  }

  // ── GitHub fetch ──────────────────────────────────────────────────────────

  private String defaultBranch(GithubCoords c, String token) {
    Map<String, Object> repo = JsonUtil.toMap(getJson(GITHUB_API + "/repos/" + c.owner() + "/" + c.repo(), token));
    return (String) repo.get("default_branch");
  }

  @SuppressWarnings("unchecked")
  private List<String> listBlobs(GithubCoords c, String branch, String token) {
    String url = GITHUB_API + "/repos/" + c.owner() + "/" + c.repo() + "/git/trees/" + branch + "?recursive=1";
    Map<String, Object> resp = JsonUtil.toMap(getJson(url, token));
    if (Boolean.TRUE.equals(resp.get("truncated"))) {
      log.warn("GitHub tree for {}/{}@{} is truncated; some files may be missing", c.owner(), c.repo(), branch);
    }
    List<Map<String, Object>> tree = (List<Map<String, Object>>) resp.getOrDefault("tree", List.of());
    return tree.stream()
        .filter(t -> "blob".equals(t.get("type")))
        .map(t -> (String) t.get("path"))
        .toList();
  }

  /** Parent directory of the shallowest {@code pages.json} (TermX) or {@code SUMMARY.md} (GitBook)
   * in the tree, or "" if at the root. */
  private String autodetectDir(List<String> blobs) {
    return blobs.stream()
        .filter(p -> p.equals("pages.json") || p.endsWith("/pages.json")
            || p.equals("SUMMARY.md") || p.endsWith("/SUMMARY.md"))
        .min(Comparator.comparingInt(p -> p.split("/").length))
        .map(p -> p.contains("/") ? StringUtils.substringBeforeLast(p, "/") : "")
        .orElseThrow(() -> new RuntimeException("no pages.json (TermX) or SUMMARY.md (GitBook) found in the repository"));
  }

  // Percent-encodes the path so asset names with spaces/special characters resolve on the CDN.
  private static String rawUrl(GithubCoords c, String branch, String path) {
    try {
      return new URI("https", "raw.githubusercontent.com",
          "/" + c.owner() + "/" + c.repo() + "/" + branch + "/" + path, null).toASCIIString();
    } catch (java.net.URISyntaxException e) {
      throw new RuntimeException("bad path: " + path, e);
    }
  }

  private String fetchRaw(GithubCoords c, String branch, String path, String token) {
    String url = rawUrl(c, branch, path);
    HttpResponse<String> resp = send(url, token, "text/plain");
    if (resp.statusCode() == 404) {
      return null;
    }
    if (resp.statusCode() >= 400) {
      throw new RuntimeException("failed to fetch " + path + " (HTTP " + resp.statusCode() + ")");
    }
    return resp.body();
  }

  private String getJson(String url, String token) {
    HttpResponse<String> resp = send(url, token, "application/vnd.github+json");
    if (resp.statusCode() == 404) {
      throw new RuntimeException("GitHub resource not found (is the repo public, or is a token needed?): " + url);
    }
    if (resp.statusCode() == 403) {
      throw new RuntimeException("GitHub rate limit or access denied; provide a token: " + url);
    }
    if (resp.statusCode() >= 400) {
      throw new RuntimeException("GitHub request failed (HTTP " + resp.statusCode() + "): " + url);
    }
    return resp.body();
  }

  private HttpResponse<String> send(String url, String token, String accept) {
    HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).GET()
        .header("Accept", accept)
        .header("User-Agent", "termx-wiki-import");
    if (StringUtils.isNotBlank(token)) {
      b.header("Authorization", "Bearer " + token);
    }
    try {
      return http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    } catch (Exception e) {
      throw new RuntimeException("GitHub request failed: " + url, e);
    }
  }

  // ── pages.json normalization (wiki-ssg -> round-trip field names) ──────────

  /** Reduces each page-content object to the round-trip parser's fields ({@code name, slug, lang,
   * ct}) — the wiki-ssg export names content-type {@code contentType} and adds
   * {@code description/keywords/modifiedAt}, which must not reach the strict record deserializer.
   * Collects slug -> name for every content along the way. */
  @SuppressWarnings("unchecked")
  private void normalizeNodes(Object node, Map<String, String> slugToName) {
    if (node instanceof List<?> list) {
      list.forEach(n -> normalizeNodes(n, slugToName));
    } else if (node instanceof Map<?, ?> raw) {
      Map<String, Object> map = (Map<String, Object>) raw;
      Object contents = map.get("contents");
      if (contents instanceof List<?> cl) {
        for (Object c : cl) {
          if (c instanceof Map<?, ?> rawc) {
            Map<String, Object> cm = (Map<String, Object>) rawc;
            if (cm.get("ct") == null && cm.get("contentType") != null) {
              cm.put("ct", cm.get("contentType"));
            }
            cm.keySet().retainAll(List.of("name", "slug", "lang", "ct"));
            if (cm.get("slug") != null && cm.get("name") != null) {
              slugToName.put(cm.get("slug").toString(), cm.get("name").toString());
            }
          }
        }
      }
      normalizeNodes(map.get("children"), slugToName);
      // Keep only what the round-trip parser deserializes; drop extras like the exported `tags`.
      map.keySet().retainAll(List.of("code", "contents", "children"));
    }
  }

  // ── repo coordinates ──────────────────────────────────────────────────────

  record GithubCoords(String owner, String repo, String branch, String dir) {
    static GithubCoords resolve(WikiGithubImportRequest req) {
      if (StringUtils.isNotBlank(req.getUrl())) {
        return parseUrl(req.getUrl(), req.getBranch(), req.getDir());
      }
      if (StringUtils.isBlank(req.getOwner()) || StringUtils.isBlank(req.getRepo())) {
        throw new RuntimeException("provide a GitHub url, or owner + repo");
      }
      return new GithubCoords(req.getOwner(), req.getRepo(), req.getBranch(), req.getDir());
    }

    // Accepts https://github.com/<owner>/<repo>[/tree/<branch>[/<dir...>]] (and github.com/... ,
    // trailing .git). Explicit branch/dir from the request win over anything parsed from the URL.
    static GithubCoords parseUrl(String url, String branchOverride, String dirOverride) {
      String s = url.trim().replaceFirst("^https?://", "").replaceFirst("^github\\.com/", "");
      String[] parts = s.split("/");
      if (parts.length < 2) {
        throw new RuntimeException("cannot parse GitHub url: " + url);
      }
      String owner = parts[0];
      String repo = StringUtils.removeEnd(parts[1], ".git");
      String branch = branchOverride;
      String dir = dirOverride;
      if (parts.length >= 4 && "tree".equals(parts[2])) {
        if (StringUtils.isBlank(branch)) {
          branch = parts[3];
        }
        if (StringUtils.isBlank(dir) && parts.length > 4) {
          dir = String.join("/", List.of(parts).subList(4, parts.length));
        }
      }
      return new GithubCoords(owner, repo, branch, dir);
    }
  }
}
