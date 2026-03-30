package org.termx.modeler.fhir;

import com.kodality.commons.model.Identifier;
import com.kodality.commons.model.LocalizedName;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4b.formats.JsonParser;
import org.hl7.fhir.r4b.model.ContactPoint;
import org.hl7.fhir.r4b.model.UsageContext;
import org.termx.core.fhir.BaseFhirMapper;
import org.termx.modeler.structuredefinition.StructureDefinition;
import org.termx.modeler.structuredefinition.StructureDefinitionVersion;
import org.termx.ts.ContactDetail;
import org.termx.ts.ContactDetail.Telecom;
import org.termx.ts.Copyright;
import org.termx.ts.UseContext;

public class StructureDefinitionFhirMapper extends BaseFhirMapper {

  public static String toFhirId(StructureDefinition sd, StructureDefinitionVersion ver) {
    return sd.getCode() + SEPARATOR + ver.getVersion();
  }

  public static StructureDefinition fromFhir(String json) {
    try {
      org.hl7.fhir.r4b.model.StructureDefinition fhir =
          (org.hl7.fhir.r4b.model.StructureDefinition) new JsonParser().parse(json);
      return fromFhir(fhir, json);
    } catch (IOException e) {
      throw new RuntimeException("Failed to parse StructureDefinition JSON", e);
    }
  }

  public static StructureDefinition fromFhir(org.hl7.fhir.r4b.model.StructureDefinition fhir, String rawJson) {
    StructureDefinition sd = new StructureDefinition();

    // Header fields
    sd.setUrl(fhir.getUrl());
    sd.setCode(fhir.hasName() ? fhir.getName() : fhir.hasId() ? fhir.getId() : null);
    sd.setName(fhir.hasTitle() ? fhir.getTitle() : fhir.hasName() ? fhir.getName() : null);
    sd.setParent(fhir.hasBaseDefinition() ? fhir.getBaseDefinition() : null);
    sd.setPublisher(fhir.hasPublisher() ? fhir.getPublisher() : null);
    sd.setExperimental(fhir.hasExperimental() ? fhir.getExperimental() : null);

    // Title as LocalizedName
    if (fhir.hasTitle()) {
      String lang = fhir.hasLanguage() ? fhir.getLanguage() : "en";
      sd.setTitle(new LocalizedName(Map.of(lang, fhir.getTitle())));
    }

    // Description as LocalizedName
    if (fhir.hasDescription()) {
      String lang = fhir.hasLanguage() ? fhir.getLanguage() : "en";
      sd.setDescription(new LocalizedName(Map.of(lang, fhir.getDescription())));
    }

    // Purpose as LocalizedName
    if (fhir.hasPurpose()) {
      String lang = fhir.hasLanguage() ? fhir.getLanguage() : "en";
      sd.setPurpose(new LocalizedName(Map.of(lang, fhir.getPurpose())));
    }

    // Copyright
    if (fhir.hasCopyright()) {
      Copyright copyright = new Copyright();
      copyright.setHolder(fhir.getCopyright());
      if (fhir.hasJurisdiction() && !fhir.getJurisdiction().isEmpty()) {
        copyright.setJurisdiction(fhir.getJurisdiction().get(0).hasText()
            ? fhir.getJurisdiction().get(0).getText()
            : null);
      }
      sd.setCopyright(copyright);
    }

    // Identifiers
    if (fhir.hasIdentifier()) {
      sd.setIdentifiers(fhir.getIdentifier().stream()
          .map(i -> {
            Identifier id = new Identifier();
            id.setSystem(i.getSystem());
            id.setValue(i.getValue());
            return id;
          })
          .toList());
    }

    // Contacts
    if (fhir.hasContact()) {
      sd.setContacts(fhir.getContact().stream().map(c -> {
        ContactDetail contact = new ContactDetail();
        contact.setName(c.hasName() ? c.getName() : null);
        if (c.hasTelecom()) {
          contact.setTelecoms(c.getTelecom().stream().map(t -> {
            Telecom telecom = new Telecom();
            telecom.setSystem(t.hasSystem() ? t.getSystem().toCode() : null);
            telecom.setValue(t.hasValue() ? t.getValue() : null);
            telecom.setUse(t.hasUse() ? t.getUse().toCode() : null);
            return telecom;
          }).toList());
        }
        return contact;
      }).toList());
    }

    // UseContext
    if (fhir.hasUseContext()) {
      sd.setUseContext(fhir.getUseContext().stream().map(uc -> {
        UseContext useContext = new UseContext();
        useContext.setType(uc.hasCode() ? uc.getCode().getCode() : null);
        if (uc.hasValueCodeableConcept() && uc.getValueCodeableConcept().hasCoding()) {
          useContext.setValue(uc.getValueCodeableConcept().getCodingFirstRep().getCode());
        }
        return useContext;
      }).toList());
    }

    // Version fields (populated on the SD for transport, merged by the service)
    sd.setContent(rawJson);
    sd.setContentFormat("json");
    sd.setContentType(fhir.hasKind() ? fhir.getKind().toCode() : null);
    sd.setVersion(fhir.hasVersion() ? fhir.getVersion() : null);
    sd.setFhirId(fhir.hasVersion() ? fhir.getVersion() : null);
    sd.setStatus(fhir.hasStatus() ? fhir.getStatus().toCode() : "draft");

    return sd;
  }
}
