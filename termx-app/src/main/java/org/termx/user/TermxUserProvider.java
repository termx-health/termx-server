package org.termx.user;

import com.kodality.commons.client.HttpClient;
import com.kodality.commons.util.JsonUtil;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import org.termx.core.user.User;
import org.termx.core.user.UserProvider;

@Requires(property = "nsoft.url")
@Singleton
public class TermxUserProvider extends UserProvider {
  private final HttpClient client;

  public TermxUserProvider(@Value("${nsoft.url}") String nsoftUrl) {
    this.client = new HttpClient(nsoftUrl);
  }

  @Override
  public List<User> getUsers() {
    CompletableFuture<List<NsoftUser>> request = client.GET("/info/users", JsonUtil.getListType(NsoftUser.class));
    return request.join().stream().map(u -> new User().setSub(u.getEmail()).setName(getName(u)).setPrivileges(getPrivileges(u))).collect(Collectors.toList());
  }

  private String getName(NsoftUser user) {
    if (user.getFirstname() != null && user.getLastname() != null) {
      return String.join(",", user.getLastname(), user.getFirstname());
    }
    return user.getEmail();
  }

  private Set<String> getPrivileges(NsoftUser user) {
    if (user.getPermissions() == null) {
      return Set.of();
    }
    return new HashSet<>(user.getPermissions());
  }


  @Getter
  @Setter
  private static class NsoftUser {
    private Long id;
    private String firstname;
    private String lastname;
    private String email;
    private List<String> permissions;
  }
}
