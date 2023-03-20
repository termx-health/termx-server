package com.kodality.termserver.ext.common;

import com.kodality.termserver.auth.CommonSessionInfo;
import com.kodality.termserver.auth.CommonSessionProvider;
import com.kodality.termserver.auth.auth.SessionInfo;
import com.kodality.termserver.auth.auth.SessionStore;
import com.kodality.termserver.auth.auth.UserPermissionService;
import io.micronaut.http.HttpRequest;
import jakarta.inject.Singleton;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class TermserverCommonSessionProvider implements CommonSessionProvider {
  private final UserPermissionService userPermissionService;

  @Override
  public Optional<CommonSessionInfo> get() {
    return SessionStore.get().map(this::toCommonSessionInfo);
  }

  @Override
  public Optional<CommonSessionInfo> get(HttpRequest httpRequest) {
    return SessionStore.get(httpRequest).map(this::toCommonSessionInfo);
  }

  @Override
  public void checkPermitted(String resourceId, String resourceType, String action) {
    userPermissionService.checkPermitted(resourceId, resourceType, action);
  }

  @Override
  public Runnable wrap(Runnable runnable) {
    return SessionStore.wrap(runnable);
  }

  @Override
  public void setLocal(CommonSessionInfo sessionInfo) {
    SessionStore.setLocal(toTermserverSessionInfo(sessionInfo));
  }

  @Override
  public void clearLocal() {
    SessionStore.clearLocal();
  }

  private CommonSessionInfo toCommonSessionInfo(SessionInfo sess) {
    CommonSessionInfo common = new CommonSessionInfo();
    common.setUsername(sess.getUsername());
    common.setPrivileges(sess.getPrivileges());
    common.setLang(sess.getLang());
    return common;
  }

  private SessionInfo toTermserverSessionInfo(CommonSessionInfo common) {
    SessionInfo sess = new SessionInfo();
    sess.setUsername(common.getUsername());
    sess.setPrivileges(common.getPrivileges());
    sess.setLang(common.getLang());
    return sess;
  }
}
