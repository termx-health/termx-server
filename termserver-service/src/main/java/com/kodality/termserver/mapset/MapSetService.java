package com.kodality.termserver.mapset;

import com.kodality.termserver.commons.model.model.QueryResult;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class MapSetService {
  private final MapSetRepository repository;
  private final MapSetVersionService mapSetVersionService;

  public Optional<MapSet> get(String id) {
    return Optional.ofNullable(repository.load(id));
  }

  @Transactional
  public void create(MapSet mapSet) {
    repository.create(mapSet);
  }

  public QueryResult<MapSet> query(MapSetQueryParams params) {
    QueryResult<MapSet> mapSets = repository.query(params);
    if (params.isDecorated()) {
      mapSets.getData().forEach(this::decorate);
    }
    return mapSets;
  }

  private void decorate(MapSet mapSet) {
    mapSet.setVersions(mapSetVersionService.getVersions(mapSet.getId()));
  }
}
