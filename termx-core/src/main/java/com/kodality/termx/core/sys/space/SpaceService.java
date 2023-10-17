package com.kodality.termx.core.sys.space;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.core.ApiError;
import com.kodality.termx.sys.space.Space;
import com.kodality.termx.sys.space.Space.SpaceIntegration;
import com.kodality.termx.sys.space.SpaceQueryParams;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class SpaceService {
  private final SpaceRepository repository;

  @Transactional
  public Space save(Space space) {
    validate(space);
    prepare(space);
    repository.save(space);
    return space;
  }

  private void validate(Space space) {
    Space currentSpace = repository.load(space.getCode());
    if (currentSpace != null && !currentSpace.getId().equals(space.getId())) {
      throw ApiError.TC108.toApiException(Map.of("code", space.getCode()));
    }
  }

  private void prepare(Space space) {
    SpaceIntegration si = space.getIntegration();
    if (si != null && si.getGithub() != null && StringUtils.isEmpty(si.getGithub().getRepo())) {
      si.setGithub(null);
    }
    if (si != null && si.getGithub() != null && si.getGithub().getDirs() != null) {
      si.getGithub().getDirs().values().removeAll(List.of(""));
      if (si.getGithub().getDirs().values().size() != new HashSet<>(si.getGithub().getDirs().values()).size()) {
        throw ApiError.TC107.toApiException();
      }
    }
  }

  public Space load(Long id) {
    return repository.load(id);
  }

  public Space load(String code) {
    return repository.load(code);
  }

  public QueryResult<Space> query(SpaceQueryParams params) {
    return repository.query(params);
  }

}
