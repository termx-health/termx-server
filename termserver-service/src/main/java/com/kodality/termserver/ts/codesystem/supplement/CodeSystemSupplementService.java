package com.kodality.termserver.ts.codesystem.supplement;

import com.kodality.termserver.codesystem.CodeSystem;
import com.kodality.termserver.codesystem.CodeSystemQueryParams;
import com.kodality.termserver.codesystem.Designation;
import com.kodality.termserver.codesystem.EntityProperty;
import com.kodality.termserver.codesystem.EntityPropertyValue;
import com.kodality.termserver.ts.codesystem.CodeSystemService;
import com.kodality.termserver.codesystem.CodeSystemSupplement;
import com.kodality.termserver.codesystem.CodeSystemSupplementType;
import com.kodality.termserver.ts.codesystem.designation.DesignationService;
import com.kodality.termserver.ts.codesystem.entityproperty.EntityPropertyService;
import com.kodality.termserver.ts.codesystem.entitypropertyvalue.EntityPropertyValueService;
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

  public CodeSystemSupplement getSupplement(Long id) {
    return decorate(repository.getSupplement(id));
  }

  @Transactional
  public void save(CodeSystemSupplement supplement, String codeSystem) {
    if (supplement.getTargetType().equals(CodeSystemSupplementType.property)) {
      EntityProperty property = (EntityProperty) supplement.getTarget();
      entityPropertyService.save(property, codeSystem);
      supplement.setTarget(property);
    }
    supplement.setCodeSystem(codeSystem);
    supplement.setCreated(supplement.getCreated() == null ? OffsetDateTime.now() : supplement.getCreated());
    repository.save(supplement);
  }

  @Transactional
  public void save(CodeSystemSupplement supplement, Long codeSystemEntityVersionId) {
    if (supplement.getTargetType().equals(CodeSystemSupplementType.propertyValue)) {
      EntityPropertyValue propertyValue = (EntityPropertyValue) supplement.getTarget();
      entityPropertyValueService.save(propertyValue, codeSystemEntityVersionId);
      supplement.setTarget(propertyValue);
    }
    if (supplement.getTargetType().equals(CodeSystemSupplementType.designation)) {
      Designation designation = (Designation) supplement.getTarget();
      designationService.save(designation, codeSystemEntityVersionId);
      supplement.setTarget(designation);
    }
    String codeSystem = codeSystemService.query(new CodeSystemQueryParams().setCodeSystemEntityVersionId(codeSystemEntityVersionId)).findFirst().map(CodeSystem::getId).orElse(null);
    supplement.setCodeSystem(codeSystem);
    supplement.setCreated(supplement.getCreated() == null ? OffsetDateTime.now() : supplement.getCreated());
    repository.save(supplement);
  }

  private CodeSystemSupplement decorate(CodeSystemSupplement supplement) {
    if (CodeSystemSupplementType.property.equals(supplement.getTargetType())) {
      supplement.setTarget(entityPropertyService.getProperty(((EntityProperty) supplement.getTarget()).getId()));
    }
    if (CodeSystemSupplementType.propertyValue.equals(supplement.getTargetType())) {
      supplement.setTarget(entityPropertyValueService.load(((EntityPropertyValue) supplement.getTarget()).getId()));
    }

    if (CodeSystemSupplementType.designation.equals(supplement.getTargetType())) {
      supplement.setTarget(designationService.get(((Designation) supplement.getTarget()).getId()).orElse(null));
    }
    return supplement;
  }
}
