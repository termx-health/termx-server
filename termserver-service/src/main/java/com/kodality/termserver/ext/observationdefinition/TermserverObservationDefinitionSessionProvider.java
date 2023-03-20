package com.kodality.termserver.ext.observationdefinition;

import com.kodality.termserver.auth.auth.SessionInfo;
import com.kodality.termserver.auth.auth.SessionStore;
import com.kodality.termserver.auth.auth.UserPermissionService;
import com.kodality.termsupp.auth.ObservationDefinitionSessionInfo;
import com.kodality.termsupp.auth.ObservationDefinitionSessionProvider;
import io.micronaut.http.HttpRequest;
import jakarta.inject.Singleton;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class TermserverObservationDefinitionSessionProvider implements ObservationDefinitionSessionProvider {
  private final UserPermissionService userPermissionService;

  @Override
  public Optional<ObservationDefinitionSessionInfo> get() {
    return SessionStore.get().map(this::toObservationDefinitionSessionInfo);
  }

  @Override
  public Optional<ObservationDefinitionSessionInfo> get(HttpRequest httpRequest) {
    return SessionStore.get(httpRequest).map(this::toObservationDefinitionSessionInfo);
  }

  @Override
  public void checkPermitted(String resourceId, String resourceType, String action) {
    userPermissionService.checkPermitted(resourceId, resourceType, action);
  }

  private ObservationDefinitionSessionInfo toObservationDefinitionSessionInfo(SessionInfo sess) {
    ObservationDefinitionSessionInfo def = new ObservationDefinitionSessionInfo();
    def.setUsername(sess.getUsername());
    def.setPrivileges(sess.getPrivileges());
    def.setLang(sess.getLang());
    return def;
  }
}
