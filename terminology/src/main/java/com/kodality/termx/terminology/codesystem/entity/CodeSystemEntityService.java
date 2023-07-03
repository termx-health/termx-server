package com.kodality.termx.terminology.codesystem.entity;

import com.kodality.termx.ts.codesystem.CodeSystemEntity;
import java.util.List;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class CodeSystemEntityService {
  private final CodeSystemEntityRepository repository;

  public CodeSystemEntity load(Long id) {
    return repository.load(id);
  }

  @Transactional
  public CodeSystemEntity save(CodeSystemEntity entity) {
    repository.save(entity);
    return entity;
  }

  @Transactional
  public void batchSave(List<CodeSystemEntity> entities, String codeSystem) {
    repository.batchUpsert(entities, codeSystem);
  }

  @Transactional
  public void cancel(Long id) {
    repository.cancel(id);
  }
}
