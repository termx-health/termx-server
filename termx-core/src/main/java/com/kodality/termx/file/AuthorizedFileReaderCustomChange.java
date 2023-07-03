package com.kodality.termx.file;

import com.kodality.commons.micronaut.liquibase.FileReaderCustomChange;
import com.kodality.termx.auth.SessionInfo;
import com.kodality.termx.auth.SessionStore;
import java.util.Set;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public abstract class AuthorizedFileReaderCustomChange extends FileReaderCustomChange {

  @Override
  protected void handleFile(String name, byte[] content) {
    try {
      SessionInfo sessionInfo = new SessionInfo();
      sessionInfo.setUsername("liquibase");
      sessionInfo.setPrivileges(Set.of("*.*.edit", "*.*.view", "*.*.publish"));
      SessionStore.setLocal(sessionInfo);

      handleMigrationFile(name, content);
    } finally {
      SessionStore.clearLocal();
    }
  }

  abstract protected void handleMigrationFile(String name, byte[] content);
}
