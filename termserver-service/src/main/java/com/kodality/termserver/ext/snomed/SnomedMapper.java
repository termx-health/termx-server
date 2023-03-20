package com.kodality.termserver.ext.snomed;

import com.kodality.termserver.ts.CaseSignificance;
import com.kodality.termserver.ts.PublicationStatus;
import com.kodality.termserver.ts.codesystem.CodeSystemEntityVersion;
import com.kodality.termserver.ts.codesystem.Concept;
import com.kodality.termserver.ts.codesystem.Designation;
import com.kodality.termserver.ts.codesystem.EntityProperty;
import com.kodality.termserver.ts.codesystem.EntityPropertyType;
import com.kodality.termserver.snomed.concept.SnomedConcept;
import com.kodality.termserver.snomed.description.SnomedDescription;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Singleton;

@Singleton
public class SnomedMapper {
  private static final String SNOMED = "snomed-ct";

  public Concept toConcept(SnomedConcept snomedConcept) {
    Concept concept = new Concept();
    concept.setCode(snomedConcept.getConceptId());
    concept.setVersions(List.of(toConceptVersion(snomedConcept)));
    concept.setCodeSystem(SNOMED);
    return concept;
  }

  private CodeSystemEntityVersion toConceptVersion(SnomedConcept snomedConcept) {
    CodeSystemEntityVersion version = new CodeSystemEntityVersion();
    version.setCode(snomedConcept.getConceptId());
    version.setCodeSystem(SNOMED);
//    version.setAssociations(snomedConcept.getRelationships() == null ? new ArrayList<>() : snomedConcept.getRelationships().stream().map(this::toConceptAssociation).collect(Collectors.toList()));
    version.setDesignations(snomedConcept.getDescriptions() == null ? new ArrayList<>() : snomedConcept.getDescriptions().stream().map(this::toConceptDesignation).collect(Collectors.toList()));
    version.setStatus(PublicationStatus.draft);
    return version;
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
      property.setDescription(t.getPt().getTerm());
      property.setStatus(t.isActive() ? PublicationStatus.active : PublicationStatus.retired);
      return property;
    }).collect(Collectors.toList());
  }
}
