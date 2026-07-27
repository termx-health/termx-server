package org.termx.user;

import com.kodality.commons.oauth.OAuthenticatedHttpClient;
import com.kodality.commons.util.JsonUtil;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Requires(property = "keycloak.url")
@Requires(property = "auth.mock.enabled", notEquals = StringUtils.TRUE)
@Singleton
public class OAuthUserHttpClient {
  private final OAuthenticatedHttpClient httpClient;

  /**
   * When set, enumerate users by Keycloak group rather than realm-wide. Groups whose name matches
   * this search term are resolved (`GET /groups?search=`), then their members unioned
   * (`GET /groups/{id}/members`). Unset (default) keeps the realm-wide `GET /users` — which is
   * unbounded and page-truncates on a large realm, so a deployment scoping its users to one group
   * sets this instead of forking the client.
   */
  private final String groupSearch;

  public OAuthUserHttpClient(@Value("${keycloak.url}") String url,
                             @Value("${keycloak.users.group-search:}") String groupSearch,
                             TermxOAuthTokenClient tokenClient) {
    this.httpClient = new OAuthenticatedHttpClient(url, tokenClient);
    this.groupSearch = groupSearch;
  }

  public CompletableFuture<List<OAuthUser>> getUsers() {
    if (StringUtils.isEmpty(groupSearch)) {
      return httpClient.GET("/users", JsonUtil.getListType(OAuthUser.class));
    }
    return getGroups().thenCompose(groups ->
        forkJoin(groups.stream().map(g -> getGroupMembers(g.id)).toList())
            .thenApply(members -> members.stream()
                .flatMap(Collection::stream)
                // A user in several matched groups is returned once (OAuthUser has no value equality,
                // so dedup on the Keycloak id, keeping first occurrence).
                .collect(Collectors.toMap(OAuthUser::getId, u -> u, (first, dup) -> first, LinkedHashMap::new))
                .values().stream().toList()));
  }

  private CompletableFuture<List<GroupRepresentation>> getGroups() {
    return httpClient.GET("/groups?search=" + groupSearch, JsonUtil.getListType(GroupRepresentation.class));
  }

  private CompletableFuture<List<OAuthUser>> getGroupMembers(String groupId) {
    return httpClient.GET("/groups/" + groupId + "/members", JsonUtil.getListType(OAuthUser.class));
  }

  public List<String> getUserRoles(String kcUserId) {
    var roles = getKeycloakUserRoleMappings(kcUserId)
        .thenApply(List::of);
    var groups = getKeycloakUserGroups(kcUserId)
        .thenCompose(g -> forkJoin(g.stream().map(this::getKeycloakGroupRoleMappings).toList()))
        .thenApply(Function.identity());

    return forkJoin(List.of(roles, groups)).thenApply(res -> res.stream()
        .flatMap(Collection::stream)
        .map(OAuthUserHttpClient::parseRoleMappings)
        .flatMap(Collection::stream)
        .distinct()
        .toList()).join();
  }


  private CompletableFuture<List<GroupRepresentation>> getKeycloakUserGroups(String kcUserId) {
    return httpClient.GET("/users/" + kcUserId + "/groups", JsonUtil.getListType(GroupRepresentation.class));
  }

  private CompletableFuture<MappingsRepresentation> getKeycloakUserRoleMappings(String kcUserId) {
    return httpClient.GET("/users/" + kcUserId + "/role-mappings", MappingsRepresentation.class);
  }

  private CompletableFuture<MappingsRepresentation> getKeycloakGroupRoleMappings(GroupRepresentation g) {
    return httpClient.GET("/groups/" + g.id + "/role-mappings", MappingsRepresentation.class);
  }


  private static List<String> parseRoleMappings(MappingsRepresentation mappings) {
    if (mappings.clientMappings == null) {
      return List.of();
    }

    return mappings.clientMappings.values()
        .stream()
        .flatMap(cm -> cm.mappings.stream())
        .map(m -> m.name)
        .toList();
  }


  private static <T> CompletableFuture<List<T>> forkJoin(List<CompletableFuture<T>> futures) {
    return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).thenCompose(ignored -> {
      var res = futures.stream().map(CompletableFuture::join).toList();
      return CompletableFuture.completedFuture(res);
    });
  }

  @Getter
  @Setter
  private static class GroupRepresentation {
    private String id;
    private String name;
  }

  @Getter
  @Setter
  private static class MappingsRepresentation {
    private Map<String, ClientMappingsRepresentation> clientMappings;

    @Getter
    @Setter
    private static class ClientMappingsRepresentation {
      private String client;
      private List<RoleRepresentation> mappings;
    }

    @Getter
    @Setter
    private static class RoleRepresentation {
      private String name;
    }
  }
}
