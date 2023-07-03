package com.kodality.termx.terminology.mapset.entity;

import com.kodality.termx.ts.mapset.MapSetEntity;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class MapSetEntityService {
  private final MapSetEntityRepository repository;

  public MapSetEntity load(Long id) {
    return repository.load(id);
  }

  @Transactional
  public MapSetEntity save(MapSetEntity entity) {
    repository.save(entity);
    return entity;
  }

  @Transactional
  public void cancel(Long id) {
    repository.cancel(id);
  }
}
