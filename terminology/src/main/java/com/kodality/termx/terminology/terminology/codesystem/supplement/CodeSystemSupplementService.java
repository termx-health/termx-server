package com.kodality.termx.terminology.terminology.codesystem.supplement;

import com.kodality.termx.terminology.terminology.codesystem.designation.DesignationService;
import com.kodality.termx.terminology.terminology.codesystem.entityproperty.EntityPropertyService;
import com.kodality.termx.terminology.terminology.codesystem.entitypropertyvalue.EntityPropertyValueService;
import com.kodality.termx.ts.codesystem.CodeSystemSupplement;
import com.kodality.termx.ts.codesystem.CodeSystemSupplementType;
import com.kodality.termx.ts.codesystem.Designation;
import com.kodality.termx.ts.codesystem.EntityProperty;
import com.kodality.termx.ts.codesystem.EntityPropertyValue;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class CodeSystemSupplementService {
  private final CodeSystemSupplementRepository repository;
  private final DesignationService designationService;
  private final EntityPropertyService entityPropertyService;
  private final EntityPropertyValueService entityPropertyValueService;

  public List<CodeSystemSupplement> getSupplements(String codeSystem) {
    return repository.getSupplements(codeSystem);
  }

  public Optional<CodeSystemSupplement> load(Long id) {
    return Optional.ofNullable(repository.load(id)).map(this::decorate);
  }

  @Transactional
  public void save(CodeSystemSupplement supplement, String codeSystem) {
    if (supplement.getTargetType().equals(CodeSystemSupplementType.property)) {
      EntityProperty property = (EntityProperty) supplement.getTarget();
      entityPropertyService.save(codeSystem, property);
      supplement.setTarget(property);
    }
    supplement.setCodeSystem(codeSystem);
    supplement.setCreated(supplement.getCreated() == null ? OffsetDateTime.now() : supplement.getCreated());
    repository.save(supplement);
  }

  @Transactional
  public void save(CodeSystemSupplement supplement, Long codeSystemEntityVersionId, String codeSystem) {
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
    supplement.setCodeSystem(codeSystem);
    supplement.setCreated(supplement.getCreated() == null ? OffsetDateTime.now() : supplement.getCreated());
    repository.save(supplement);
  }

  private CodeSystemSupplement decorate(CodeSystemSupplement supplement) {
    if (CodeSystemSupplementType.property.equals(supplement.getTargetType())) {
      supplement.setTarget(entityPropertyService.load(supplement.getCodeSystem(), ((EntityProperty) supplement.getTarget()).getId()).orElse(null));
    }
    if (CodeSystemSupplementType.propertyValue.equals(supplement.getTargetType())) {
      supplement.setTarget(entityPropertyValueService.load(((EntityPropertyValue) supplement.getTarget()).getId()).orElse(null));
    }

    if (CodeSystemSupplementType.designation.equals(supplement.getTargetType())) {
      supplement.setTarget(designationService.load(((Designation) supplement.getTarget()).getId()).orElse(null));
    }
    return supplement;
  }
}
