package com.kodality.termserver.codesystem.supplement;

import com.kodality.termserver.codesystem.CodeSystem;
import com.kodality.termserver.codesystem.CodeSystemQueryParams;
import com.kodality.termserver.codesystem.CodeSystemService;
import com.kodality.termserver.codesystem.CodeSystemSupplement;
import com.kodality.termserver.codesystem.CodeSystemSupplementType;
import com.kodality.termserver.codesystem.designation.DesignationService;
import com.kodality.termserver.codesystem.entityproperty.EntityPropertyService;
import com.kodality.termserver.codesystem.entitypropertyvalue.EntityPropertyValueService;
import java.time.OffsetDateTime;
import java.util.List;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class CodeSystemSupplementService {
  private final CodeSystemSupplementRepository repository;
  private final CodeSystemService codeSystemService;
  private final DesignationService designationService;
  private final EntityPropertyService entityPropertyService;
  private final EntityPropertyValueService entityPropertyValueService;

  public List<CodeSystemSupplement> getSupplements(String codeSystem) {
    return repository.getSupplements(codeSystem);
  }

  @Transactional
  public void save(CodeSystemSupplement supplement, String codeSystem) {
    if (supplement.getType().equals(CodeSystemSupplementType.property)) {
      entityPropertyService.save(supplement.getPropertySupplement(), codeSystem);
    }
    supplement.setCodeSystem(codeSystem);
    supplement.setCreated(supplement.getCreated() == null ? OffsetDateTime.now() : supplement.getCreated());
    repository.save(supplement);
  }

  @Transactional
  public void save(CodeSystemSupplement supplement, Long codeSystemEntityVersionId) {
    if (supplement.getType().equals(CodeSystemSupplementType.propertyValue)) {
      entityPropertyValueService.save(supplement.getPropertyValueSupplement(), codeSystemEntityVersionId);
    }
    if (supplement.getType().equals(CodeSystemSupplementType.designation)) {
      designationService.save(supplement.getDesignationSupplement(), codeSystemEntityVersionId);
    }
    String codeSystem = codeSystemService.query(new CodeSystemQueryParams().setCodeSystemEntityVersionId(codeSystemEntityVersionId)).findFirst().map(CodeSystem::getId).orElse(null);
    supplement.setCodeSystem(codeSystem);
    supplement.setCreated(supplement.getCreated() == null ? OffsetDateTime.now() : supplement.getCreated());
    repository.save(supplement);
  }


}
