package com.kodality.termx.core.fhir;

import com.kodality.commons.model.LocalizedName;
import com.kodality.kefhir.core.model.search.SearchCriterion;
import com.kodality.termx.core.sys.provenance.Provenance;
import com.kodality.termx.ts.ContactDetail;
import com.kodality.termx.ts.ContactDetail.Telecom;
import com.kodality.termx.ts.Language;
import com.kodality.zmei.fhir.Extension;
import com.kodality.zmei.fhir.datatypes.ContactPoint;
import com.kodality.zmei.fhir.datatypes.Identifier;
import com.kodality.zmei.fhir.datatypes.Narrative;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

public abstract class BaseFhirMapper {
  public static final String SEPARATOR = "--";

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

  protected static Extension toFhirWebSourceExtension(String url, String id) {
    return new Extension("http://hl7.org/fhir/tools/StructureDefinition/web-source").setValueUrl(url + "/fhir/ValueSet/" + id);
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

  protected static List<Identifier> toFhirIdentifiers(List<com.kodality.commons.model.Identifier> identifiers) {
    if (identifiers == null) {
      return null;
    }
    return identifiers.stream().map(i -> new Identifier().setSystem(i.getSystem()).setValue(i.getValue())).collect(Collectors.toList());
  }

  public static String toFhirName(LocalizedName name, String lang) {
    if (name == null) {
      return null;
    }
    return name.getOrDefault(Optional.ofNullable(lang).orElse(Language.en), name.values().stream().findFirst().orElse(null));
  }

  protected static Narrative toFhirText(String narrative) {
    return narrative == null ? null : new Narrative().setDiv(narrative);
  }

  protected static LocalDate toFhirDate(List<Provenance> provenances, String activity) {
    return Optional.ofNullable(provenances).flatMap(list -> list.stream().filter(p -> activity.equals(p.getActivity()))
        .max(Comparator.comparing(Provenance::getDate)).map(p -> p.getDate().toLocalDate())).orElse(null);
  }


  protected static LocalizedName fromFhirName(String name, String lang) {
    if (name == null) {
      return null;
    }
    return new LocalizedName(Map.of(Optional.ofNullable(lang).orElse(Language.en), name));
  }

  protected static List<com.kodality.commons.model.Identifier> fromFhirIdentifiers(List<com.kodality.zmei.fhir.datatypes.Identifier> identifiers) {
    if (identifiers == null) {
      return null;
    }
    return identifiers.stream().map(i -> new com.kodality.commons.model.Identifier(i.getSystem(), i.getValue())).collect(Collectors.toList());
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
}
