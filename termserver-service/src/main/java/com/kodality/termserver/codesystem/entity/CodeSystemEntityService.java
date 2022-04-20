package com.kodality.termserver.codesystem.entity;

import com.kodality.termserver.codesystem.CodeSystemEntity;
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
}
