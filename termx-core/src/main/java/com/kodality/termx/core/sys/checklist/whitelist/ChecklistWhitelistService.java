package com.kodality.termx.core.sys.checklist.whitelist;

import com.kodality.termx.sys.checklist.Checklist.ChecklistWhitelist;
import java.util.List;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class ChecklistWhitelistService {
  private final ChecklistWhitelistRepository repository;

  @Transactional
  public void save(Long checklistId, List<ChecklistWhitelist> whitelist) {
    repository.retain(checklistId, whitelist);
    if (whitelist != null) {
      whitelist.forEach(w -> repository.save(checklistId, w));
    }
  }
}
