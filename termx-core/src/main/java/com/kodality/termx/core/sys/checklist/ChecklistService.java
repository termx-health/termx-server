package com.kodality.termx.core.sys.checklist;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.core.sys.checklist.whitelist.ChecklistWhitelistService;
import com.kodality.termx.sys.checklist.Checklist;
import com.kodality.termx.sys.checklist.ChecklistQueryParams;
import java.util.List;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class ChecklistService {
  private final ChecklistRepository repository;
  private final ChecklistWhitelistService whitelistService;

  public QueryResult<Checklist> query(ChecklistQueryParams params) {
    return repository.query(params);
  }

  @Transactional
  public void save(List<Checklist> checklist, String resourceType, String resourceId) {
    repository.retain(resourceType, resourceId, checklist);
    checklist.forEach(c -> {
      c.setResourceType(resourceType).setResourceId(resourceId);
      repository.save(c);
      whitelistService.save(c.getId(), c.getWhitelist());
    });

  }

  public Checklist load(Long id) {
    return repository.load(id);
  }
}
