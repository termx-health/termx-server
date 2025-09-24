package com.kodality.termx.core.fhir;

import com.kodality.commons.model.LocalizedName;
import com.kodality.kefhir.core.model.search.SearchCriterion;
import com.kodality.termx.core.sys.provenance.Provenance;
import com.kodality.termx.ts.ConfigurationAttribute;
import com.kodality.termx.ts.ContactDetail;
import com.kodality.termx.ts.ContactDetail.Telecom;
import com.kodality.termx.ts.Language;
import com.kodality.termx.ts.OtherTitle;
import com.kodality.termx.ts.Topic;
import com.kodality.termx.ts.UseContext;
import com.kodality.termx.ts.codesystem.Concept;
import com.kodality.zmei.fhir.Element;
import com.kodality.zmei.fhir.Extension;
import com.kodality.zmei.fhir.datatypes.CodeableConcept;
import com.kodality.zmei.fhir.datatypes.Coding;
import com.kodality.zmei.fhir.datatypes.ContactPoint;
import com.kodality.zmei.fhir.datatypes.Identifier;
import com.kodality.zmei.fhir.datatypes.Narrative;
import com.kodality.zmei.fhir.datatypes.RelatedArtifact;
import com.kodality.zmei.fhir.datatypes.UsageContext;
import com.kodality.zmei.fhir.resource.DomainResource;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r5.model.Narrative.NarrativeStatus;

public abstract class BaseFhirMapper {
  public static final String SEPARATOR = "--";
  public static final String VERSION_IDENTIFIER_TYPE = "version";

  public static String[] parseCompositeId(String id) {
    id = URLDecoder.decode(id, StandardCharsets.UTF_8);
    if (!id.contains(SEPARATOR)) {
      return new String[]{id, null};
    }
    String one = StringUtils.substringBefore(id, SEPARATOR);
    String two = StringUtils.substringAfter(id, SEPARATOR);
    return new String[]{one, StringUtils.isEmpty(two) ? null : two};
  }

  protected static Integer getOffset(SearchCriterion fhir) {
    return fhir.getCount() == null || fhir.getCount() == 0 ? 0 : (fhir.getPage() - 1) * fhir.getCount();
  }

  public static Map<String, String> getSimpleParams(SearchCriterion fhir) {
    return fhir.getRawParams().keySet().stream()
        .filter(k -> CollectionUtils.isNotEmpty(fhir.getRawParams().get(k)))
        .collect(Collectors.toMap(k -> k, k -> fhir.getRawParams().get(k).get(0)));
  }

  protected static Extension toFhirWebSourceExtension(String url, String id, String resourceType) {
    return new Extension("http://hl7.org/fhir/tools/StructureDefinition/web-source").setValueUrl(url + "/fhir/" + resourceType+ "/" + id);
  }

  protected static Extension toFhirWebSourceExtension(String url) {
      return new Extension("http://hl7.org/fhir/StructureDefinition/web-source").setValueUrl(url);
  }

  protected static Extension toFhirSourceReferenceExtension(String url, String value) {
    return new Extension(url).setValueUri(value);
  }

  protected static Extension toFhirReplacesExtension(String value) {
    return new Extension("http://hl7.org/fhir/StructureDefinition/replaces").setValueUri(value);
  }

  protected static Extension toFhirOtherTitleExtension(String url, OtherTitle otherTitle) {
    Extension extension = new Extension(url);
    extension.setExtension(List.of(
        new Extension().setUrl("name").setValueString(otherTitle.getName()),
        new Extension().setUrl("preferred").setValueBoolean(otherTitle.isPreferred())));
    return extension;
  }

  protected static Extension toFhirVersionDescriptionExtension(String description) {
    Extension extension = new Extension("https://fhir.ee/StructureDefinition/version-description");
    extension.setValueString(description);
    return extension;
  }

  protected static List<com.kodality.zmei.fhir.datatypes.ContactDetail> toFhirContacts(List<ContactDetail> cds) {
    return cds == null ? null : cds.stream().map(c -> new com.kodality.zmei.fhir.datatypes.ContactDetail()
            .setName(c.getName())
            .setTelecom(c.getTelecoms() == null ? null : c.getTelecoms().stream().map(t -> new ContactPoint()
                .setSystem(t.getSystem())
                .setValue(t.getValue())
                .setUse(t.getUse())
            ).toList()))
        .toList();
  }

  protected static List<com.kodality.zmei.fhir.datatypes.ContactDetail> toFhirContacts(String name) {
    return name == null ? null : List.of(new com.kodality.zmei.fhir.datatypes.ContactDetail().setName(name));
  }

  protected static List<Identifier> toFhirIdentifiers(List<com.kodality.commons.model.Identifier> identifiers,
                                                      List<com.kodality.commons.model.Identifier> versionIdentifiers) {
    if (identifiers == null && versionIdentifiers == null) {
      return null;
    }
    List<Identifier> fhirIdentifiers = Optional.ofNullable(identifiers).orElse(new ArrayList<>()).stream()
        .map(i -> new Identifier().setSystem(i.getSystem()).setValue(i.getValue())).collect(Collectors.toList());
    fhirIdentifiers.addAll(Optional.ofNullable(versionIdentifiers).orElse(new ArrayList<>()).stream()
        .filter(vi -> identifiers == null || identifiers.stream().noneMatch(i -> (i.getSystem() + i.getValue()).equals(vi.getSystem() + vi.getValue())))
        .map(vi -> new Identifier().setSystem(vi.getSystem()).setValue(vi.getValue()).setType(new CodeableConcept(new Coding(VERSION_IDENTIFIER_TYPE))))
        .toList());
    return fhirIdentifiers;
  }

  public static String toFhirName(LocalizedName name, String lang) {
    if (name == null) {
      return null;
    }
    String fhirName = name.getOrDefault(Optional.ofNullable(lang).orElse(Language.en), name.values().stream().findFirst().orElse(null));
    return StringUtils.isEmpty(fhirName) ? null : fhirName;
  }

  protected static Narrative toFhirText(String narrative) {
    return narrative == null ? null : new Narrative().setDiv(narrative).setStatus(NarrativeStatus.GENERATED.toCode());
  }

  protected static CodeableConcept toFhirTopic(Topic topic) {
    return topic == null ? null : new CodeableConcept()
        .setText(topic.getText())
        .setCoding(Optional.ofNullable(topic.getTags()).orElse(List.of()).stream().map(t -> new Coding().setCode(t)).toList());
  }

  protected static List<UsageContext> toFhirUseContext(List<UseContext> useContext) {
    return useContext == null ? null : useContext.stream().map(ctx -> new UsageContext()
        .setCode(new Coding(ctx.getType()))
        .setValueCodeableConcept(new CodeableConcept().setText(ctx.getValue()))).toList();
  }

  protected static LocalDate toFhirDate(List<Provenance> provenances, String activity) {
    return Optional.ofNullable(provenances).flatMap(list -> list.stream().filter(p -> activity.equals(p.getActivity()))
        .max(Comparator.comparing(Provenance::getDate)).map(p -> p.getDate().toLocalDate())).orElse(null);
  }

  protected static OffsetDateTime toFhirOffsetDateTime(List<Provenance> provenances) {
    return Optional.ofNullable(provenances).orElse(List.of()).stream()
        .max(Comparator.comparing(Provenance::getDate)).map(Provenance::getDate).orElse(null);
  }

  protected static LocalizedName fromFhirName(String name, String lang, Element extension) {
    if (name == null) {
      return null;
    }
    LocalizedName localizedName =  new LocalizedName(Map.of(Optional.ofNullable(lang).orElse(Language.en), name));
    if (extension == null) {
      return localizedName;
    }
    extension.getExtensions("http://hl7.org/fhir/StructureDefinition/translation").forEach(translation -> {
      String translationLang = translation.getExtension("lang").map(Extension::getValueCode).orElse(null);
      String translationContent = translation.getExtension("content").map(Extension::getValueString).orElse(null);
      if (translationLang != null && translationContent != null) {
        localizedName.add(translationLang, translationContent);
      }
    });
    return localizedName;
  }

  protected static List<com.kodality.commons.model.Identifier> fromFhirIdentifiers(List<com.kodality.zmei.fhir.datatypes.Identifier> identifiers) {
    if (identifiers == null) {
      return null;
    }
    return identifiers.stream().filter(i -> i.getType() == null || i.getType().getCoding() == null ||
            i.getType().getCoding().stream().noneMatch(c -> VERSION_IDENTIFIER_TYPE.equals(c.getCode())))
        .map(i -> new com.kodality.commons.model.Identifier(i.getSystem(), i.getValue())).collect(Collectors.toList());
  }

  protected static List<com.kodality.commons.model.Identifier> fromFhirVersionIdentifiers(List<com.kodality.zmei.fhir.datatypes.Identifier> identifiers) {
    if (identifiers == null) {
      return null;
    }
    return identifiers.stream().filter(i -> i.getType() != null && i.getType().getCoding() != null &&
            i.getType().getCoding().stream().anyMatch(c -> VERSION_IDENTIFIER_TYPE.equals(c.getCode())))
        .map(i -> new com.kodality.commons.model.Identifier(i.getSystem(), i.getValue())).collect(Collectors.toList());
  }

  protected static List<ContactDetail> fromFhirContacts(List<com.kodality.zmei.fhir.datatypes.ContactDetail> details) {
    if (details == null) {
      return null;
    }
    return details.stream().map(d -> {
      List<Telecom> telecoms = d.getTelecom() == null ? null : d.getTelecom().stream()
          .map(t -> new Telecom().setSystem(t.getSystem()).setUse(t.getUse()).setValue(t.getValue())).toList();
      return new ContactDetail().setName(d.getName()).setTelecoms(telecoms);
    }).toList();
  }

  protected static String fromFhirContactsName(List<com.kodality.zmei.fhir.datatypes.ContactDetail> details) {
    if (CollectionUtils.isEmpty(details)) {
      return null;
    }
    return details.get(0).getName();
  }

  protected static List<Extension> toFhirTranslationExtension(LocalizedName name, String language) {
    if (name == null) {
      return null;
    }

    List<Extension> translations = name.keySet().stream()
        .filter(l -> !l.equals(Optional.ofNullable(language).orElse(Language.en)))
        .filter(l -> StringUtils.isNotEmpty(name.get(l)))
        .map(l -> {
          return new Extension("http://hl7.org/fhir/StructureDefinition/translation")
              .<Extension>addExtension(new Extension("lang").setValueCode(l))
              .<Extension>addExtension(new Extension("content").setValueString(name.get(l)));
        }).toList();

    if (CollectionUtils.isEmpty(translations)) {
      return null;
    }
    return translations;
  }

  protected static void toFhir(DomainResource resource, List<ConfigurationAttribute> configurationAttributes, Map<String, Concept> concepts) {
    configurationAttributes.forEach(attr -> {
      Concept concept = concepts.get(attr.getAttribute());
      if (concept == null || !((Boolean) concept.getLastVersion().map(v -> v.getPropertyValue("fhir").orElse(false)).orElse(false))) {
        return;
      }
      Extension extension = new Extension();
      concept.getLastVersion().flatMap(v -> v.getPropertyValue("url")).ifPresentOrElse(url -> extension.setUrl((String) url), () -> extension.addPrimitiveExtension("code", new Extension().setValueCode(attr.getAttribute())));
      Optional.ofNullable(attr.getLanguage()).ifPresent(lang -> extension.addPrimitiveExtension("language", new Extension().setValueCode(lang)));
      Optional.ofNullable(attr.getValue()).ifPresent(c -> extension.addPrimitiveExtension("content", new Extension().setValueString(c)));
      resource.addExtension(extension);
    });
  }

  protected static void toFhirRelatedArtifacts(DomainResource resource, List<String> relatedArtifacts) {
    relatedArtifacts.forEach(a -> resource.addExtension(new Extension("http://hl7.org/fhir/StructureDefinition/workflow-relatedArtifact")
        .setValueRelatedArtifact(new RelatedArtifact().setResource(a))));
  }

  protected static String fromFhirVersionDescriptionExtension(List<Extension> extensions) {
    if (extensions == null) {
      return null;
    }
    return extensions.stream().filter(e -> "https://fhir.ee/StructureDefinition/version-description".equals(e.getUrl())).findFirst().map(Extension::getValueString).orElse(null);
  }
}
