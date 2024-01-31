package com.kodality.termx.core.sys.checklist;

import com.kodality.commons.model.QueryResult;
import com.kodality.termx.core.sys.checklist.whitelist.ChecklistWhitelistService;
import com.kodality.termx.core.sys.provenance.Provenance;
import com.kodality.termx.core.sys.provenance.ProvenanceService;
import com.kodality.termx.core.sys.provenance.ProvenanceUtil;
import com.kodality.termx.sys.checklist.Checklist;
import com.kodality.termx.sys.checklist.ChecklistQueryParams;
import io.micronaut.core.util.CollectionUtils;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class ChecklistService {
  private final ChecklistRepository repository;
  private final ChecklistWhitelistService whitelistService;
  private final ProvenanceService provenanceService;

  public QueryResult<Checklist> query(ChecklistQueryParams params) {
    return repository.query(params);
  }

  @Transactional
  public void save(List<Checklist> checklist, String resourceType, String resourceId) {
    provenanceResource(resourceType, resourceId, () -> {
      repository.retain(resourceType, resourceId, checklist);
      checklist.forEach(c -> {
        c.setResourceType(resourceType).setResourceId(resourceId);
        repository.save(c);
        whitelistService.save(c.getId(), c.getWhitelist());
      });
    });
  }

  public Checklist load(Long id) {
    return repository.load(id);
  }


  public void provenanceResource(String resourceType, String resourceId, Runnable save) {
    Map<Long, String> before = query(new ChecklistQueryParams().setResourceType(resourceType).setResourceId(resourceId).all()).getData()
        .stream().collect(Collectors.toMap(Checklist::getId, checklist -> checklist.getRule().getCode()));
    save.run();
    Map<Long, String> after = query(new ChecklistQueryParams().setResourceType(resourceType).setResourceId(resourceId).all()).getData()
        .stream().collect(Collectors.toMap(Checklist::getId, checklist -> checklist.getRule().getCode()));
    provenanceService.create(new Provenance("checklist", resourceType, resourceId)
        .setChanges(ProvenanceUtil.diff(before, after))
        .created(CollectionUtils.isEmpty(before)));
  }
}
