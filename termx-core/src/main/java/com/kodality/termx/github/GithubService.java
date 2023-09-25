package com.kodality.termx.github;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import com.kodality.commons.cache.CacheManager;
import com.kodality.commons.client.HttpClient;
import com.kodality.commons.client.HttpClientError;
import com.kodality.commons.util.JsonUtil;
import com.kodality.commons.util.MapUtil;
import com.kodality.termx.auth.SessionStore;
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
    c.getFiles().stream().filter(f -> f.getContent() != null).forEach(f -> {
      Map<String, String> req = Map.of("encoding", "utf-8", "content", f.getContent());
      String blobResp = post(base + "/blobs", req);
      f.setSha(JsonUtil.read(blobResp, "$.sha"));
    });
    Map<String, Object> treeObj = Map.of("base_tree", c.getLastCommitSha(), "tree", c.getFiles().stream().map(f -> {
      return MapUtil.toMap("path", f.getPath(), "mode", "100644", "type", "blob", "sha", f.getSha());
    }).toList());
    String treeSha = JsonUtil.read(post(base + "/trees", treeObj), "$.sha");

    Map<String, Object> commit = Map.of(
        "message", c.getMessage(),
        "author", MapUtil.toMap("name", c.getAuthorName(), "email", c.getAuthorEmail()),
        "parents", List.of(c.getLastCommitSha()),
        "tree", treeSha
    );
    String commitSha = JsonUtil.read(post(base + "/commits", commit), "$.sha");

    patch("/repos/" + repo + "/git/refs/heads/main", Map.of("sha", commitSha));
  }

  public List<GithubContent> listContents(String repo, String path) {
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
    List<Map<String, Object>> branchesResp = JsonUtil.fromJson(get("/repos/" + repo + "/branches"), JsonUtil.getListType(Map.class));
    Map<String, Object> branch = branchesResp.stream().filter(r -> List.of("master", "main").contains((String) r.get("name"))).findFirst()
        .orElseGet(() -> branchesResp.get(0));
    String sha = (String) ((Map) branch.get("commit")).get("sha");
    Map<String, Object> treesResp = JsonUtil.toMap(get("/repos/" + repo + "/git/trees/" + sha + "?recursive=true"));
    List<Map<String, Object>> tree = (List<Map<String, Object>>) treesResp.get("tree");
    return tree.stream().filter(f -> f.get("type").equals("blob")).map(f -> {
      String content = ((Long) f.get("size")) == 0 ? "" : getBlob((String) f.get("url"));
      return new GithubContent().setPath((String) f.get("path")).setSha((String) f.get("sha")).setContent(content);
    }).toList();
  }

  public String getLastCommitSha(String repo) {
    return JsonUtil.read(get("/repos/" + repo + "/branches/main"), "$.commit.sha");
  }


  // #status(String repo, String dir, List<GithubContent> current) is more optimal.
  public GithubStatus status(String repo, List<GithubContent> current) {
    Map<String, GithubContent> githubContents =
        current.stream().map(c -> getContent(repo, c.getPath())).filter(Objects::nonNull).collect(toMap(GithubContent::getPath, c -> c));
    Map<String, GithubContent> localContents = current.stream().collect(toMap(GithubContent::getPath, c -> c));
    return calculateStatus(repo, githubContents, localContents);
  }

  //TODO: can seek only one dir currently.
  public GithubStatus status(String repo, String dir, List<GithubContent> current) {
    Map<String, GithubContent> githubContents = listContents(repo, dir).stream().collect(toMap(GithubContent::getPath, c -> c));
    Map<String, GithubContent> localContents = current.stream().collect(toMap(GithubContent::getPath, c -> c));
    return calculateStatus(repo, githubContents, localContents);
  }

  private GithubStatus calculateStatus(String repo, Map<String, GithubContent> githubContents, Map<String, GithubContent> localContents) {
    GithubStatus status = new GithubStatus();
    status.setSha(getLastCommitSha(repo));
    status.setFiles(Sets.union(localContents.keySet(), githubContents.keySet()).stream().collect(
        toMap(p -> p, p -> calculateStatus(githubContents.get(p), localContents.get(p)))
    ));
    return status;
  }

  private String calculateStatus(GithubContent github, GithubContent local) {
    return github == null ? GithubStatus.A
        : local == null ? GithubStatus.D
        : !github.getSha().equals(calculateSha(local.getContent())) ? GithubStatus.M
        : GithubStatus.U;
  }

  private String calculateSha(String input) {
    try {
      byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
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
