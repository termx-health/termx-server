package com.kodality.termx.terminology.terminology.codesystem.designation;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.ts.codesystem.Designation;
import com.kodality.termx.ts.codesystem.DesignationQueryParams;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class DesignationService {
  private final DesignationRepository repository;

  public Optional<Designation> load(Long id) {
    return Optional.ofNullable(repository.load(id));
  }

  public QueryResult<Designation> query(DesignationQueryParams params) {
    return repository.query(params);
  }

  public List<Designation> loadAll(Long codeSystemEntityVersionId) {
    return repository.loadAll(codeSystemEntityVersionId);
  }

  @Transactional
  public void save(List<Designation> designations, Long codeSystemEntityVersionId) {
    repository.retain(designations, codeSystemEntityVersionId);
    if (designations != null) {
      designations.forEach(designation -> save(designation, codeSystemEntityVersionId));
    }
  }

  @Transactional
  public void batchUpsert(Map<Long, List<Designation>> designations) {
    List<Entry<Long, List<Designation>>> entries = designations.entrySet().stream().toList();
    repository.retain(entries);
    repository.save(entries.stream().flatMap(e -> e.getValue().stream().map(v -> Pair.of(e.getKey(), v))).toList());
  }

  @Transactional
  public void save(Designation designation, Long codeSystemEntityVersionId) {
    repository.save(designation, codeSystemEntityVersionId);
  }

  @Transactional
  public void delete(Long propertyId) {
    repository.delete(propertyId);
  }

}
