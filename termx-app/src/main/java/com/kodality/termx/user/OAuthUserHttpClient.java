package com.kodality.termx.user;

import com.kodality.commons.oauth.OAuthenticatedHttpClient;
import com.kodality.commons.util.JsonUtil;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Requires(property = "keycloak.url")
@Singleton
@RequiredArgsConstructor
public class OAuthUserHttpClient {
  private final OAuthenticatedHttpClient httpClient;

  public OAuthUserHttpClient(@Value("${keycloak.url}") String url, TermxOAuthTokenClient tokenClient) {
    httpClient = new OAuthenticatedHttpClient(url, tokenClient);
  }

  public CompletableFuture<List<OAuthUser>> getUsers() {
    return httpClient.GET("/users", JsonUtil.getListType(OAuthUser.class));
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
