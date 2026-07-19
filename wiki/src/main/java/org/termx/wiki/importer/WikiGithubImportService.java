package org.termx.wiki.importer;

import com.github.slugify.Slugify;
import com.kodality.commons.util.JsonUtil;
import org.termx.core.sys.space.SpaceService;
import org.termx.sys.space.Space;
import org.termx.wiki.SpaceGithubDataWikiHandler;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
    int pages = gitbook
        ? buildFromGitbook(content, coords, branch, prefix, req)
        : buildFromTermx(content, coords, branch, dir, prefix, blobs, req);

    log.info("importing {} page(s) into space {} from {}/{}@{} ({}, {})",
        pages, req.getSpaceId(), coords.owner(), coords.repo(), branch, dir.isEmpty() ? "/" : dir,
        gitbook ? "gitbook" : "termx");
    wikiHandler.saveContent(req.getSpaceId(), content);

    return new WikiGithubImportResult(coords.owner() + "/" + coords.repo(), branch, dir, pages);
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

    // The parser re-slugifies each content from its name on save (PageContentService.prepare) and
    // then keys the .md files it applies by that slug — so key the markdown the same way, not by
    // the file name (a repo's stored slug can differ from slugify(name)). Only .md referenced by
    // pages.json is fetched (orphans are skipped so the parser can't NPE); attachments/resources
    // are not imported.
    Slugify slugify = Slugify.builder().build();
    Map<String, String> keyToPath = new LinkedHashMap<>();
    for (String path : blobs) {
      if (!path.endsWith(".md") || !path.startsWith(prefix)) {
        continue;
      }
      String fileSlug = StringUtils.removeEnd(StringUtils.substringAfterLast("/" + path, "/"), ".md");
      String name = slugToName.get(fileSlug);
      if (name != null) {
        keyToPath.put(slugify.slugify(name) + ".md", path);
      }
    }
    Map<String, String> fetched = fetchAll(keyToPath, coords, branch, req.getToken(), false);
    content.putAll(fetched);
    return fetched.size();
  }

  // ── GitBook (SUMMARY.md tree + linked .md) ─────────────────────────────────

  private int buildFromGitbook(Map<String, String> content, GithubCoords coords, String branch,
                               String prefix, WikiGithubImportRequest req) {
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
    buildGbStructure(roots, pagesJson, content, bodies, lang, slugify);
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
                                Map<String, String> bodies, String lang, Slugify slugify) {
    for (GbNode n : nodes) {
      String slug = slugify.slugify(n.title);
      String key = n.href != null ? "gitbook:" + n.href : "gitbook-section:" + n.title;
      String code = UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();

      Map<String, Object> contentObj = new LinkedHashMap<>();
      contentObj.put("name", n.title);
      contentObj.put("slug", slug);
      contentObj.put("lang", lang);
      contentObj.put("ct", "markdown");

      List<Map<String, Object>> children = new ArrayList<>();
      buildGbStructure(n.children, children, content, bodies, lang, slugify);

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

  private String fetchRaw(GithubCoords c, String branch, String path, String token) {
    String url = GITHUB_RAW + "/" + c.owner() + "/" + c.repo() + "/" + branch + "/" + path;
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
