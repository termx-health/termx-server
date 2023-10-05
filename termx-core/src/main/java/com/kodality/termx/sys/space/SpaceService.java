package com.kodality.termx.sys.space;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.ApiError;
import com.kodality.termx.sys.space.Space.SpaceIntegration;
import java.util.HashSet;
import java.util.List;
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
    prepare(space);
    repository.save(space);
    return space;
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
