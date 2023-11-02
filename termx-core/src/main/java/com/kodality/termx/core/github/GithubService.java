package com.kodality.termx.core.github;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.kodality.commons.cache.CacheManager;
import com.kodality.commons.client.HttpClient;
import com.kodality.commons.client.HttpClientError;
import com.kodality.commons.util.JsonUtil;
import com.kodality.commons.util.MapUtil;
import com.kodality.termx.core.auth.SessionStore;
import com.kodality.termx.core.github.GithubService.GithubContent.GithubContentEncoding;
import com.kodality.termx.core.github.GithubService.GithubTreeItem.GithubTreeType;
import com.sun.jdi.request.InvalidRequestStateException;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpHeaders;
import jakarta.inject.Singleton;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.postgresql.shaded.com.ongres.scram.common.bouncycastle.base64.Base64;

import static com.kodality.commons.stream.Collectors.toMap;

@Requires(property = "github.client.id")
@Singleton
public class GithubService {
  public static final String GITHUB_OAUTH = "https://github.com/login/oauth";
  public static final String GITHUB_API = "https://api.github.com";
  private final HttpClient http;
  private final CacheManager cacheManager;
  private final ObjectMapper objectMapper;

  private static final String MAIN_BRANCH = "main";

  @Value("${github.client.id}")
  private String clientId;
  @Value("${github.client.secret}")
  private String clientSecret;
  @Value("${github.app-name}")
  private String appName;

  public GithubService() {
    // Github oauth token lives 8 hours, we expire cache after 7
    // Token may be refreshed instead of expiring in the future
    // Cache is not distributed - if multiple instances of server running, Github integration may not work properly
    this.cacheManager = new CacheManager();
    this.cacheManager.initCache("user-token", 1000, TimeUnit.HOURS.toSeconds(7));
    this.cacheManager.initCache("state", 1000, TimeUnit.MINUTES.toSeconds(10));
    this.objectMapper = new ObjectMapper(); // github specific json mapper. main reason is the requirement to write null values (to delete files)
    this.http = new HttpClient() {
      @Override
      public Builder builder(String path) {
        return super.builder(path)
            .setHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
            .setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
      }
    };
  }

  public boolean isAuthorized(String repo) {
    String user = SessionStore.require().getUsername();
    return getUserToken(user) != null && isAppInstalled(user, repo);
  }

  public String getAuthRedirect(String returnUrl, String repository) {
    String state = UUID.randomUUID().toString();
    this.cacheManager.getCache("state").put(state, Map.of(
        "url", returnUrl,
        "user", SessionStore.require().getUsername(),
        "repo", repository
    ));
    return GITHUB_OAUTH + "/authorize?client_id=" + clientId + "&state=" + state;
  }

  public String authorizeUser(String state, String code) {
    Map<String, String> stateObj = (Map<String, String>) this.cacheManager.getCache("state").get(state);
    if (stateObj == null) {
      throw new InvalidRequestStateException("invalid 'state'");
    }
    String user = stateObj.get("user");
    String repo = stateObj.get("repo");
    String token = getAccessToken(code);
    this.cacheManager.getCache("user-token").put(user, token);
    if (isAppInstalled(user, repo)) {
      return stateObj.get("url");
    }
    return "https://github.com/apps/" + this.appName + "/installations/new?state=" + state;
  }

  private String getAccessToken(String code) {
    String response = http.POST(GITHUB_OAUTH + "/access_token", Map.of(
        "client_id", clientId,
        "client_secret", clientSecret,
        "code", code
    )).join().body();
    return JsonUtil.read(response, "$.access_token");
  }

  private boolean isAppInstalled(String user, String repo) {
    String group = StringUtils.substringBefore(repo, "/");
    String installations = this.http.execute(buildBaseRequest("/user/installations", getUserToken(user)).GET().build()).body();
    List<String> installedOn = JsonUtil.read(installations, ".installations.*.account.login");
    return installedOn.contains(group);
  }


  public void commit(String repo, GithubCommit c) {
    String base = "/repos/" + repo + "/git";
    if (c.getLastCommitSha() == null) {
      c.setLastCommitSha(getLastCommitSha(repo));
    }

    // commit files with content
    c.getFiles().stream().filter(f -> f.getContent() != null).forEach(f -> {
      Map<String, String> req = Map.of("encoding", f.getEncoding(), "content", f.getContent());
      String blobResp = post(base + "/blobs", req);
      f.setSha(JsonUtil.read(blobResp, "$.sha"));
    });

    // create new git tree using generated SHA values
    Map<String, Object> treeObj = Map.of(
        "base_tree", c.getLastCommitSha(),
        "tree", c.getFiles().stream().map(f -> MapUtil.toMap(
            "path", f.getPath(),
            "mode", "100644",
            "type", "blob",
            "sha", f.getSha()
        )).toList());
    String treeSha = JsonUtil.read(post(base + "/trees", treeObj), "$.sha");

    // main commit
    Map<String, Object> commit = Map.of(
        "message", c.getMessage(),
        "author", MapUtil.toMap("name", c.getAuthorName(), "email", c.getAuthorEmail()),
        "parents", List.of(c.getLastCommitSha()),
        "tree", treeSha
    );
    String commitSha = JsonUtil.read(post(base + "/commits", commit), "$.sha");

    patch("/repos/" + repo + "/git/refs/heads/" + MAIN_BRANCH, Map.of("sha", commitSha));
  }

  public List<GithubContent> getContents(String repo, String path) {
    try {
      String a = get("/repos/" + repo + "/contents/" + path);
      return JsonUtil.fromJson(a, JsonUtil.getListType(GithubContent.class));
    } catch (HttpClientError e) {
      if (e.getResponse().statusCode() == 404) {
        return List.of();
      }
      throw e;
    }
  }

  public GithubContent getContent(String repo, String path) {
    try {
      String resp = get("/repos/" + repo + "/contents/" + path);
      GithubContent content = JsonUtil.fromJson(resp, GithubContent.class);
      if ("base64".equals(JsonUtil.read(resp, "$.encoding"))) {
        content.setContent(new String(Base64.decode(content.getContent().replaceAll("\n", ""))));
      }
      return content;
    } catch (HttpClientError e) {
      if (e.getResponse().statusCode() == 404) {
        return null;
      }
      throw e;
    }
  }

  public GithubTree getTree(String repo, String path) {
    try {
      // The limit for the tree array is 100,000 entries with a maximum size of 7 MB when using the recursive parameter.
      String resp = get("/repos/" + repo + "/git/trees/" + path + "?recursive=true");
      return JsonUtil.fromJson(resp, GithubTree.class);
    } catch (HttpClientError e) {
      if (e.getResponse().statusCode() == 404) {
        return null;
      }
      throw e;
    }
  }

  public String getBlob(String url) {
    try {
      Map<String, Object> resp = JsonUtil.toMap(get(url));
      if ("base64".equals(resp.get("encoding"))) {
        return new String(Base64.decode(((String) resp.get("content")).replaceAll("\n", "")));
      }
      return (String) resp.get("content");
    } catch (HttpClientError e) {
      if (e.getResponse().statusCode() == 404) {
        return null;
      }
      throw e;
    }
  }

  public List<GithubContent> readFully(String repo) {
    String mainBranch = getMainBranch(repo);
    GithubTree treesResp = getTree(repo, mainBranch);
    return treesResp.getTree().stream().filter(t -> GithubTreeType.blob.equals(t.getType())).map(t -> {
      String content = t.getSize() == 0 ? "" : getBlob(t.getUrl());
      return new GithubContent().setPath(t.getPath()).setSha(t.getSha()).setContent(content);
    }).toList();
  }

  public String getLastCommitSha(String repo) {
    return JsonUtil.read(get("/repos/" + repo + "/branches/" + MAIN_BRANCH), "$.commit.sha");
  }

  private String getMainBranch(String repo) {
    List<Map<String, Object>> branchesResp = JsonUtil.fromJson(get("/repos/" + repo + "/branches"), JsonUtil.getListType(Map.class));
    Map<String, Object> branch = branchesResp.stream()
        .filter(r -> List.of("master", MAIN_BRANCH).contains((String) r.get("name")))
        .findFirst()
        .orElseGet(() -> branchesResp.get(0));
    return (String) branch.get("name");
  }


  // #status(String repo, String dir, List<GithubContent> current) is more optimal.
  public GithubStatus status(String repo, List<GithubContent> local) {
    // absolute path -> content
    Map<String, GithubContent> localContents = local.stream().collect(toMap(GithubContent::getPath, c -> c));

    // absolute path -> tree, finds git tree for every unique path in local
    Map<String, GithubTreeItem> githubTree = localContents.keySet().stream()
        .map(p -> {
          GithubTree tree = getTree(repo, StringUtils.join(MAIN_BRANCH, ":", p));
          return getTreeAbsoluteBlobs(p, tree);
        })
        .flatMap(Collection::stream)
        .collect(toMap(GithubTreeItem::getPath, c -> c));

    return calculateBlobStatus(repo, githubTree, localContents);
  }

  public GithubStatus status(String repo, String dir, List<GithubContent> local) {
    // recursive tree, relative to 'dir'
    GithubTree treeResp = getTree(repo, StringUtils.join(MAIN_BRANCH, ":", dir));

    // absolute path -> tree
    Map<String, GithubTreeItem> githubTree = getTreeAbsoluteBlobs(dir, treeResp).stream().collect(toMap(GithubTreeItem::getPath, c -> c));
    // absolute path -> content
    Map<String, GithubContent> localContents = local.stream().collect(toMap(GithubContent::getPath, c -> c));

    return calculateBlobStatus(repo, githubTree, localContents);
  }

  private List<GithubTreeItem> getTreeAbsoluteBlobs(String dir, GithubTree tree) {
    if (tree == null) {
      return List.of();
    }
    return tree.getTree().stream()
        .filter(t -> GithubTreeType.blob.equals(t.getType()))
        .peek(t -> t.setPath(dir + "/" + t.getPath()))
        .toList();
  }

  private GithubStatus calculateBlobStatus(String repo, Map<String, GithubTreeItem> githubTree, Map<String, GithubContent> localContents) {
    // absolute paths
    SetView<String> uniquePaths = Sets.union(localContents.keySet(), githubTree.keySet());
    return new GithubStatus()
        .setSha(getLastCommitSha(repo))
        .setFiles(uniquePaths.stream().collect(toMap(p -> p, p -> {
          return calculateBlobStatus(githubTree.get(p), localContents.get(p));
        })));
  }

  private String calculateBlobStatus(GithubTreeItem github, GithubContent local) {
    if (github == null) {
      return GithubStatus.A;
    }
    if (local == null) {
      return GithubStatus.D;
    }
    if (!Objects.equals(github.getSha(), calculateSha(local))) {
      return GithubStatus.M;
    }
    return GithubStatus.U;
  }

  private String calculateSha(GithubContent content) {
    if (content.getEncoding().equals(GithubContentEncoding.base64)) {
      return calculateSha(Base64.decode(content.getContent()));
    }
    return calculateSha(content.getContent());
  }

  private String calculateSha(String src) {
    byte[] bytes = src.getBytes(StandardCharsets.UTF_8);
    return calculateSha(bytes);
  }

  private String calculateSha(byte[] bytes) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA1");
      md.update(String.format("%s %d\u0000", "blob", bytes.length).getBytes());
      md.update(bytes);
      return Hex.encodeHexString((md.digest()));
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }


  private String toJson(Object o) {
    return JsonUtil.toJson(o, objectMapper);
  }

  private String patch(String uri, Object body) {
    return this.http.execute(buildBaseRequest(uri).method("PATCH", BodyPublishers.ofString(toJson(body))).build()).body();
  }

  private String post(String uri, Object body) {
    return this.http.execute(buildBaseRequest(uri).POST(BodyPublishers.ofString(toJson(body))).build()).body();
  }

  private String get(String uri) {
    return this.http.execute(buildBaseRequest(uri).GET().build(), BodyHandlers.ofString()).body();
  }

  private Builder buildBaseRequest(String uri) {
    return buildBaseRequest(uri, getUserToken(SessionStore.require().getUsername()));
  }

  private Builder buildBaseRequest(String path, String token) {
    if (token == null) {
      throw new IllegalAccessError("Token is missing");
    }
    return http.builder(path.startsWith("http") ? path : GITHUB_API + path).setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
  }

  private String getUserToken(String user) {
    return (String) this.cacheManager.getCache("user-token").get(user);
  }


  @Getter
  @Setter
  public static class GithubCommit {
    private String lastCommitSha;
    private String message;
    private String authorName;
    private String authorEmail;
    private List<GithubContent> files;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class GithubContent {
    private String path;
    private String sha;
    // GithubContent without content will be deleted during commit;
    private String content;
    private String encoding = GithubContentEncoding.utf8;

    public interface GithubContentEncoding {
      String utf8 = "utf-8";
      String base64 = "base64";
    }
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class GithubTree {
    private String sha;
    private List<GithubTreeItem> tree;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class GithubTreeItem {
    private String path;
    /**
     * The file mode; one of
     * 100644 for file (blob),
     * 100755 for executable (blob),
     * 040000 for subdirectory (tree),
     * 160000 for submodule (commit), or
     * 120000 for a blob that specifies the path of a symlink.
     */
    private String mode;
    /**
     * Either blob, tree, or commit.
     */
    private String type;
    private String sha;
    private String url;
    private Long size;

    public interface GithubTreeType {
      String blob = "blob";
    }
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class GithubStatus {
    public static String M = "M";
    public static String U = "U";
    public static String D = "D";
    public static String A = "A";
    public static String K = "K";
    private String sha;
    private Map<String, String> files = new LinkedHashMap<>();
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class GithubDiff {
    private String left;
    private String right;
  }
}
