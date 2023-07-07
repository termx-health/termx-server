package com.kodality.termx.user;

import com.kodality.termx.taskflow.OAuthUser;
import com.kodality.termx.taskflow.OAuthUserHttpClient;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class TermxUserProvider extends UserProvider {
  private final Optional<OAuthUserHttpClient> authUserHttpClient;

  @Override
  public List<User> getUsers() {
    return authUserHttpClient.map(oAuthUserHttpClient -> oAuthUserHttpClient.getUsers().join().stream()
            .map(u -> new User().setSub(u.getUsername()).setName(getName(u))).collect(Collectors.toList()))
        .orElseGet(List::of);
  }

  private String getName(OAuthUser user) {
    if (user.getFirstName() != null && user.getLastName() != null) {
      return String.join(",", user.getLastName(), user.getFirstName());
    }
    return user.getUsername();
  }
}
