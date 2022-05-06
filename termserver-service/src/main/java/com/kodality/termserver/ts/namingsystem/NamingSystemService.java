package com.kodality.termserver.ts.namingsystem;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.namingsystem.NamingSystem;
import com.kodality.termserver.namingsystem.NamingSystemQueryParams;
import java.time.OffsetDateTime;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class NamingSystemService {
  private final NamingSystemRepository repository;

  public QueryResult<NamingSystem> query(NamingSystemQueryParams params) {
    return repository.query(params);
  }

  public Optional<NamingSystem> get(String id) {
    return Optional.ofNullable(repository.load(id));
  }

  @Transactional
  public void create(NamingSystem namingSystem) {
    namingSystem.setCreated(namingSystem.getCreated() == null ? OffsetDateTime.now() : namingSystem.getCreated());
    repository.create(namingSystem);
  }
}
