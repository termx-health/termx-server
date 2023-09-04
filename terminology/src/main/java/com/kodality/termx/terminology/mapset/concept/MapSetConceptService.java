package com.kodality.termx.terminology.mapset.concept;

import com.kodality.commons.model.QueryResult;
import com.kodality.commons.model.QueryResult.SearchResultMeta;
import com.kodality.termx.terminology.codesystem.concept.ConceptService;
import com.kodality.termx.terminology.codesystem.concept.ConceptUtil;
import com.kodality.termx.terminology.mapset.association.MapSetAssociationRepository;
import com.kodality.termx.terminology.mapset.version.MapSetVersionRepository;
import com.kodality.termx.terminology.valueset.concept.ValueSetVersionConceptService;
import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.termx.ts.codesystem.ConceptQueryParams;
import com.kodality.termx.ts.codesystem.Designation;
import com.kodality.termx.ts.mapset.MapSetAssociation;
import com.kodality.termx.ts.mapset.MapSetAssociationQueryParams;
import com.kodality.termx.ts.mapset.MapSetConcept;
import com.kodality.termx.ts.mapset.MapSetConceptQueryParams;
import com.kodality.termx.ts.mapset.MapSetVersion;
import com.kodality.termx.ts.mapset.MapSetVersion.MapSetResourceReference;
import com.kodality.termx.ts.valueset.ValueSetVersionConcept;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;

@Singleton
@RequiredArgsConstructor
public class MapSetConceptService {
  private final MapSetVersionRepository mapSetVersionService;
  private final MapSetAssociationRepository mapSetAssociationRepository;
  private final ConceptService codeSystemConceptService;
  private final ValueSetVersionConceptService valueSetConceptService;

  public QueryResult<MapSetConcept> query(String mapSet, String version, MapSetConceptQueryParams params) {
    MapSetVersion msv = mapSetVersionService.load(mapSet, version);

    if ("source".equals(params.getType()) && msv != null) {
      return querySourceConcepts(msv, params);
    }
    if ("target".equals(params.getType()) && msv != null) {
      return queryTargetConcepts(msv, params);
    }
    return QueryResult.empty();
  }

  private QueryResult<MapSetConcept> querySourceConcepts(MapSetVersion msv, MapSetConceptQueryParams params) {
    if ("code-system".equals(msv.getScope().getSourceType()) && CollectionUtils.isNotEmpty(msv.getScope().getSourceCodeSystems())) {
      String scs = msv.getScope().getSourceCodeSystems().stream().map(cs -> cs.getId() + "|" + cs.getVersion()).collect(Collectors.joining(","));
      return queryCodeSystemConcepts(msv, scs, params);
    }
    if ("value-set".equals(msv.getScope().getSourceType()) && msv.getScope().getSourceValueSet() != null) {
      return queryValueSetConcepts(msv, msv.getScope().getSourceValueSet(), params);
    }
    return QueryResult.empty();
  }

  private QueryResult<MapSetConcept> queryTargetConcepts(MapSetVersion msv, MapSetConceptQueryParams params) {
    if ("code-system".equals(msv.getScope().getTargetType()) && CollectionUtils.isNotEmpty(msv.getScope().getTargetCodeSystems())) {
      String tcs = msv.getScope().getTargetCodeSystems().stream().map(cs -> cs.getId() + "|" + cs.getVersion()).collect(Collectors.joining(","));
      return queryCodeSystemConcepts(msv, tcs, params);
    }
    if ("value-set".equals(msv.getScope().getTargetType()) && msv.getScope().getTargetValueSet() != null) {
      return queryValueSetConcepts(msv, msv.getScope().getTargetValueSet(), params);
    }
    return QueryResult.empty();
  }


  private QueryResult<MapSetConcept> queryCodeSystemConcepts(MapSetVersion msv, String cs, MapSetConceptQueryParams params) {

    ConceptQueryParams cp = new ConceptQueryParams().setCodeSystemVersions(cs).setTextContains(params.getTextContains());
    cp.setUnmapedInMapSetVersionId(params.getUnmapped() != null && params.getUnmapped() ? msv.getId() : null);
    cp.setVerifiedInMapSetVersionId(params.getVerified() != null && params.getVerified() ? msv.getId() : null);
    cp.setUnverifiedInMapSetVersionId(params.getVerified() != null && !params.getVerified() ? msv.getId() : null);
    cp.setLimit(params.getLimit());
    cp.setOffset(params.getOffset());
    cp.setSort(List.of("code"));

    QueryResult<Concept> qr = codeSystemConceptService.query(cp);

    Map<String, List<MapSetAssociation>> associations = qr.findFirst().isEmpty() || params.getUnmapped() != null && params.getUnmapped() ? Map.of() :
        mapSetAssociationRepository.query(new MapSetAssociationQueryParams().setMapSetVersionId(msv.getId()).setVerified(params.getVerified()).setSourceCodes(qr.getData().stream().map(Concept::getCode).collect(Collectors.joining(","))).all())
            .getData().stream().collect(Collectors.groupingBy(a -> a.getSource().getCode() + a.getSource().getCodeSystem()));

    List<MapSetConcept> msc = qr.getData().stream().map(c -> new MapSetConcept()
        .setCode(c.getCode())
        .setCodeSystem(c.getCodeSystem())
        .setDisplay(ConceptUtil.getDisplay(c.getVersions().stream().flatMap(v -> v.getDesignations().stream()).toList(), msv.getPreferredLanguage(), List.of()))
        .setDesignations(c.getVersions().stream().flatMap(v -> v.getDesignations().stream()).toList())
        .setAssociations(associations.getOrDefault(c.getCode() + c.getCodeSystem(), List.of()))
    ).toList();

    QueryResult<MapSetConcept> mscr = new QueryResult<>();
    mscr.setData(msc);
    mscr.setMeta(qr.getMeta());
    return mscr;
  }

  private QueryResult<MapSetConcept> queryValueSetConcepts(MapSetVersion msv, MapSetResourceReference vs, MapSetConceptQueryParams params) {
    List<ValueSetVersionConcept> concepts = valueSetConceptService.expand(vs.getId(), vs.getVersion());
    concepts = concepts.stream().filter(c -> params.getTextContains() == null || c.getConcept().getCode().contains(params.getTextContains())).toList();
    concepts = params.getLimit() >= 0 && params.getLimit() < concepts.size() ? concepts.subList(params.getOffset(), params.getLimit()) : concepts;

    Map<String, List<MapSetAssociation>> associations = CollectionUtils.isEmpty(concepts) ? Map.of() :
        mapSetAssociationRepository.query(new MapSetAssociationQueryParams().setMapSetVersionId(msv.getId()).setVerified(params.getVerified()).setSourceCodes(concepts.stream().map(c -> c.getConcept().getCode()).collect(Collectors.joining(","))).all())
            .getData().stream().collect(Collectors.groupingBy(a -> a.getSource().getCode() + a.getSource().getCodeSystem()));

    List<MapSetConcept> msc = concepts.stream().map(c -> {
      List<Designation> allDesignations = new ArrayList<>(c.getAdditionalDesignations());
      allDesignations.add(c.getDisplay());
      return new MapSetConcept()
        .setCode(c.getConcept().getCode())
        .setCodeSystem(c.getConcept().getCodeSystem())
        .setDisplay(c.getDisplay())
        .setDesignations(allDesignations)
        .setAssociations(associations.getOrDefault(c.getConcept().getCode() + c.getConcept().getCodeSystem(), List.of()));
    }).toList();

    QueryResult<MapSetConcept> mscr = new QueryResult<>();
    SearchResultMeta meta = new SearchResultMeta();
    meta.setTotal(concepts.size());
    meta.setOffset(params.getOffset());
    mscr.setMeta(meta);
    mscr.setData(msc);
    return new QueryResult<>(msc);
  }

}
