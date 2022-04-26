package com.kodality.termserver.codesystem.designation;

import com.kodality.commons.model.QueryResult;
import com.kodality.termserver.codesystem.Designation;
import com.kodality.termserver.codesystem.DesignationQueryParams;
import java.util.List;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class DesignationService {
  private final DesignationRepository repository;

  public QueryResult<Designation> query(DesignationQueryParams params) {
    return repository.query(params);
  }

  public List<Designation> loadAll(Long codeSystemEntityVersionId) {
    return repository.loadAll(codeSystemEntityVersionId);
  }

  @Transactional
  public void save(List<Designation> designations, Long codeSystemEntityVersionId) {
    repository.retain(designations, codeSystemEntityVersionId);
    repository.batchUpsert(designations, codeSystemEntityVersionId);
  }

}
