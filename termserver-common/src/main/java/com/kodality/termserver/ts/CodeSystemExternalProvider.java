package com.kodality.termserver.ts;

import com.kodality.termserver.ts.codesystem.CodeSystemEntityVersion;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public abstract class CodeSystemExternalProvider {
  private static final int BATCH_SIZE = 100;
  public List<CodeSystemEntityVersion> loadLastVersions(String codeSystem, List<String> codes) {
    if (!codeSystem.equals(getCodeSystemId())) {
      return new ArrayList<>();
    }
    return IntStream.range(0, (codes.size() + BATCH_SIZE - 1) / BATCH_SIZE)
        .mapToObj(i -> codes.subList(i * BATCH_SIZE, Math.min(codes.size(), (i + 1) * BATCH_SIZE)))
        .flatMap(batch -> loadLastVersions(batch).stream()).toList();
  }

  public abstract List<CodeSystemEntityVersion> loadLastVersions(List<String> code);

  public abstract String getCodeSystemId();
}
