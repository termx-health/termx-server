package com.kodality.termx.terminology.terminology.association;

import com.kodality.termx.terminology.ApiError;
import com.kodality.termx.terminology.terminology.codesystem.association.CodeSystemAssociationService;
import com.kodality.termx.terminology.terminology.mapset.association.MapSetAssociationService;
import com.kodality.termx.ts.codesystem.CodeSystemAssociation;
import com.kodality.termx.ts.codesystem.CodeSystemAssociationQueryParams;
import com.kodality.termx.ts.mapset.MapSetAssociation;
import com.kodality.termx.ts.mapset.MapSetAssociationQueryParams;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jakarta.inject.Singleton;
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
    List<String> requiredCodeSystems = List.of("equivalent", "source-is-narrower-than-target", "source-is-broader-than-target", "grouped-by", "is-a", "part-of", "classified-with");
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
    mapSetAssociationParams.setRelationships(code);
    mapSetAssociationParams.setLimit(1);
    Optional<MapSetAssociation> mapSetAssociation = mapSetAssociationService.query(mapSetAssociationParams).findFirst();
    if (mapSetAssociation.isPresent()) {
      throw ApiError.TE803.toApiException(Map.of("mapSet", mapSetAssociation.get().getMapSet()));
    }
  }
}
