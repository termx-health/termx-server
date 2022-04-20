package com.kodality.termserver.codesystem.entity;

import com.kodality.termserver.codesystem.CodeSystemAssociation;
import com.kodality.termserver.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.codesystem.CodeSystemEntityVersionQueryParams;
import com.kodality.termserver.codesystem.association.CodeSystemAssociationService;
import com.kodality.termserver.codesystem.designation.DesignationService;
import com.kodality.termserver.codesystem.entitypropertyvalue.EntityPropertyValueService;
import com.kodality.termserver.commons.model.model.QueryResult;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class CodeSystemEntityVersionService {
  private final DesignationService designationService;
  private final EntityPropertyValueService entityPropertyValueService;
  private final CodeSystemAssociationService codeSystemAssociationService;
  private final CodeSystemEntityVersionRepository repository;


  public CodeSystemEntityVersion getByCode(String code) {
    return repository.getByCode(code);
  }

  @Transactional
  public CodeSystemEntityVersion save(CodeSystemEntityVersion version, Long codeSystemEntityId, String codeSystem) {
    version.setCreated(version.getCreated() == null ? OffsetDateTime.now() : version.getCreated());
    repository.save(version, codeSystemEntityId);

    designationService.save(version.getDesignations(), version.getId());
    entityPropertyValueService.save(version.getPropertyValues(), version.getId());
    codeSystemAssociationService.save(prepareAssociations(version.getAssociations()), version.getId(), codeSystem);
    return version;
  }

  public QueryResult<CodeSystemEntityVersion> query(CodeSystemEntityVersionQueryParams params) {
    return repository.query(params);
  }

  private List<CodeSystemAssociation> prepareAssociations(List<CodeSystemAssociation> associations) {
    if (associations == null) {
      return new ArrayList<>();
    }
    //TODO fix get by code not enough, add more parameters like code system, version...
    associations.forEach(a -> a.setTargetId(a.getTargetId() == null ? getByCode(a.getTargetCode()).getId() : a.getTargetId()));
    return associations;
  }
}
