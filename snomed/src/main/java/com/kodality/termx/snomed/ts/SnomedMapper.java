package com.kodality.termx.snomed.ts;

import com.kodality.commons.model.LocalizedName;
import com.kodality.termx.snomed.concept.SnomedConcept.SnomedConceptName;
import com.kodality.termx.snomed.concept.SnomedConceptSearchParams;
import com.kodality.termx.ts.CaseSignificance;
import com.kodality.termx.ts.PublicationStatus;
import com.kodality.termx.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.termx.ts.codesystem.ConceptQueryParams;
import com.kodality.termx.ts.codesystem.Designation;
import com.kodality.termx.ts.codesystem.EntityProperty;
import com.kodality.termx.ts.codesystem.EntityPropertyType;
import com.kodality.termx.snomed.concept.SnomedConcept;
import com.kodality.termx.snomed.description.SnomedDescription;
import com.kodality.termx.ts.valueset.ValueSetVersionConcept.ValueSetVersionConceptValue;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.inject.Singleton;

@Singleton
public class SnomedMapper {
  private static final String SNOMED = "snomed-ct";
  private static final String SNOMED_URI = "http://snomed.info/sct";

  public Concept toConcept(SnomedConcept snomedConcept) {
    Concept concept = new Concept();
    concept.setCode(snomedConcept.getConceptId());
    concept.setVersions(List.of(toConceptVersion(snomedConcept)));
    concept.setCodeSystem(SNOMED);
    return concept;
  }

  public ValueSetVersionConceptValue toVSConcept(SnomedConcept snomedConcept) {
    ValueSetVersionConceptValue concept = new ValueSetVersionConceptValue();
    concept.setCode(snomedConcept.getConceptId());
    concept.setCodeSystem(SNOMED);
    concept.setCodeSystemUri(SNOMED_URI);
    return concept;
  }

  public SnomedConceptSearchParams toSnomedParams(ConceptQueryParams params) {
    SnomedConceptSearchParams snomedParams = new SnomedConceptSearchParams();
    snomedParams.setConceptIds(StringUtils.isNotEmpty(params.getCode()) ? Arrays.stream(params.getCode().split(",")).toList() : List.of());
    snomedParams.setTerm(params.getTextContains());
    snomedParams.setActive(true);
    snomedParams.setLimit(params.getLimit());
    return snomedParams;
  }

  public CodeSystemEntityVersion toConceptVersion(SnomedConcept snomedConcept) {
    CodeSystemEntityVersion version = new CodeSystemEntityVersion();
    version.setCode(snomedConcept.getConceptId());
    version.setCodeSystem(SNOMED);
//    version.setAssociations(snomedConcept.getRelationships() == null ? new ArrayList<>() : snomedConcept.getRelationships().stream().map(this::toConceptAssociation).collect(Collectors.toList()));
    if (CollectionUtils.isNotEmpty(snomedConcept.getDescriptions())) {
      version.setDesignations(snomedConcept.getDescriptions().stream().map(this::toConceptDesignation).collect(Collectors.toList()));
    } else  {
      version.setDesignations(toConceptDesignations(snomedConcept.getPt(), snomedConcept.getFsn()));
    }
    version.setStatus(PublicationStatus.draft);
    return version;
  }

  private List<Designation> toConceptDesignations(SnomedConceptName pt, SnomedConceptName fsn) {
    Designation designationPt = new Designation();
    designationPt.setName(pt.getTerm());
    designationPt.setLanguage(pt.getLang());
    designationPt.setDesignationType("display");
    designationPt.setPreferred(true);
    designationPt.setStatus(PublicationStatus.active);

    Designation designationFsn = new Designation();
    designationFsn.setName(fsn.getTerm());
    designationFsn.setLanguage(fsn.getLang());
    designationFsn.setDesignationType("description");
    designationFsn.setPreferred(false);
    designationFsn.setStatus(PublicationStatus.active);
    return List.of(designationPt, designationFsn);
  }

  private Designation toConceptDesignation(SnomedDescription d) {
    Designation designation = new Designation();
    designation.setName(d.getTerm());
    designation.setLanguage(d.getLang());
    designation.setDesignationType(d.getTypeId());
    designation.setCaseSignificance(d.getCaseSignificanceId() == null ? CaseSignificance.entire_term_case_insensitive : d.getCaseSignificanceId());
    designation.setStatus(d.isActive() ? PublicationStatus.active : PublicationStatus.retired);
    return designation;
  }

//  private CodeSystemAssociation toConceptAssociation(SnomedRelationship r) {
//    CodeSystemAssociation association = new CodeSystemAssociation();
//    association.setCodeSystem(SNOMED);
//    association.setAssociationType(r.getTypeId());
//    association.setTargetCode(r.getTarget().getConceptId());
//    return association;
//  }

  public List<EntityProperty> toProperties(List<SnomedConcept> types) {
    return types.stream().map(t -> {
      EntityProperty property = new EntityProperty();
      property.setType(EntityPropertyType.string);
      property.setName(t.getConceptId());
      property.setDescription(t.getPt().getTerm() != null ? new LocalizedName(Map.of(t.getPt().getLang(), t.getPt().getTerm())) : null);
      property.setStatus(t.isActive() ? PublicationStatus.active : PublicationStatus.retired);
      return property;
    }).collect(Collectors.toList());
  }
}
