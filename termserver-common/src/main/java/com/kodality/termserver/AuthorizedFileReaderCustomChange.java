package com.kodality.termserver;

import com.kodality.commons.micronaut.liquibase.FileReaderCustomChange;
import com.kodality.termserver.auth.CommonSessionInfo;
import com.kodality.termserver.auth.CommonSessionProvider;
import java.util.Set;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public abstract class AuthorizedFileReaderCustomChange extends FileReaderCustomChange {
  private final CommonSessionProvider sessionProvider;

  @Override
  protected void handleFile(String name, byte[] content) {
    try {
      CommonSessionInfo sessionInfo = new CommonSessionInfo();
      sessionInfo.setUsername("liquibase");
      sessionInfo.setPrivileges(Set.of("*.*.edit", "*.*.view", "*.*.publish"));
      sessionProvider.setLocal(sessionInfo);

      handleMigrationFile(name, content);
    } finally {
      sessionProvider.clearLocal();
    }
  }

  abstract protected void handleMigrationFile(String name, byte[] content);
}
