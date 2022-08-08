package com.kodality.termserver.common;

import com.kodality.commons.micronaut.liquibase.FileReaderCustomChange;
import com.kodality.termserver.auth.auth.SessionInfo;
import com.kodality.termserver.auth.auth.SessionStore;
import java.util.List;

public abstract class AuthorizedFileReaderCustomChange extends FileReaderCustomChange {

  @Override
  protected void handleFile(String name, byte[] content) {
    try {
      SessionInfo sessionInfo = new SessionInfo();
      sessionInfo.setUsername("liquibase");
      sessionInfo.setRoles(List.of("kts-admin"));
      SessionStore.setLocal(sessionInfo);

      handleMigrationFile(name, content);
    } finally {
      SessionStore.clearLocal();
    }
  }

  abstract protected void handleMigrationFile(String name, byte[] content);
}
