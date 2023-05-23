package com.kodality.termserver.taskflow;

import com.kodality.taskflow.api.TaskflowUserProvider;
import com.kodality.taskflow.user.TaskflowUser;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;


@Singleton
@RequiredArgsConstructor
public class TermserverTaskflowUserProvider extends TaskflowUserProvider {
  private final Optional<OAuthUserHttpClient> authUserHttpClient;

  @Override
  public List<TaskflowUser> getUsers() {
    return authUserHttpClient.map(oAuthUserHttpClient -> oAuthUserHttpClient.getUsers().join().stream().map(this::toTaskflowUser).collect(Collectors.toList()))
        .orElseGet(List::of);
  }

  private TaskflowUser toTaskflowUser(OAuthUser user) {
    return new TaskflowUser().setSub(user.getUsername()).setName(getName(user));
  }

  private String getName(OAuthUser user) {
    if (user.getFirstName() != null && user.getLastName() != null) {
      return String.join(",", user.getLastName(), user.getFirstName());
    }
    return user.getUsername();
  }
}
