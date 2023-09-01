package com.kodality.termx.terminology.mapset;


import com.kodality.termx.terminology.association.AssociationTypeService;
import com.kodality.termx.ts.association.AssociationType;
import com.kodality.termx.ts.mapset.MapSet;
import com.kodality.termx.ts.mapset.MapSetTransactionRequest;
import java.util.List;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class MapSetImportService {
  private final MapSetService mapSetService;
  private final AssociationTypeService associationTypeService;

  @Transactional
  public void importMapSet(MapSet mapSet, List<AssociationType> associationTypes) {
    associationTypeService.createIfNotExist(associationTypes);

    MapSetTransactionRequest request = new MapSetTransactionRequest();
    request.setMapSet(mapSet);
    request.setVersion(mapSet.getVersions().get(0));
    request.setAssociations(mapSet.getVersions().get(0).getAssociations());
    importMapSet(request);
  }

  @Transactional
  public void importMapSet(MapSetTransactionRequest request) {
    mapSetService.save(request);
  }
}
