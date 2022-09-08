package com.kodality.termserver.ts.association;

import com.kodality.termserver.ApiError;
import com.kodality.termserver.codesystem.CodeSystemAssociation;
import com.kodality.termserver.codesystem.CodeSystemAssociationQueryParams;
import com.kodality.termserver.mapset.MapSetAssociation;
import com.kodality.termserver.mapset.MapSetAssociationQueryParams;
import com.kodality.termserver.ts.codesystem.association.CodeSystemAssociationService;
import com.kodality.termserver.ts.mapset.association.MapSetAssociationService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class AssociationTypeDeleteService {
  private final AssociationTypeService associationTypeService;
  private final MapSetAssociationService mapSetAssociationService;
  private final CodeSystemAssociationService codeSystemAssociationService;

  @Transactional
  public void delete(String code) {
    checkAssociationTypeUsed(code);
    associationTypeService.cancel(code);
  }

  private void checkAssociationTypeUsed(String code) {
    List<String> requiredCodeSystems = List.of("relatedto", "equivalent", "equal", "wider", "subsumes", "narrower", "specializes", "inexact", "unmatched", "disjoint");
    if (requiredCodeSystems.contains(code)) {
      throw ApiError.TE801.toApiException();
    }

    CodeSystemAssociationQueryParams codeSystemAssociationParams = new CodeSystemAssociationQueryParams();
    codeSystemAssociationParams.setAssociationType(code);
    codeSystemAssociationParams.setLimit(1);
    Optional<CodeSystemAssociation> codeSystemAssociation = codeSystemAssociationService.query(codeSystemAssociationParams).findFirst();
    if (codeSystemAssociation.isPresent()) {
      throw ApiError.TE802.toApiException(Map.of("codeSystem", codeSystemAssociation.get().getCodeSystem()));
    }

    MapSetAssociationQueryParams mapSetAssociationParams = new MapSetAssociationQueryParams();
    mapSetAssociationParams.setType(code);
    mapSetAssociationParams.setLimit(1);
    Optional<MapSetAssociation> mapSetAssociation = mapSetAssociationService.query(mapSetAssociationParams).findFirst();
    if (mapSetAssociation.isPresent()) {
      throw ApiError.TE803.toApiException(Map.of("mapSet", mapSetAssociation.get().getMapSet()));
    }
  }
}
