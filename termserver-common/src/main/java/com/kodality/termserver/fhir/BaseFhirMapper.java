package com.kodality.termserver.fhir;

import com.kodality.commons.model.LocalizedName;
import com.kodality.kefhir.core.model.search.SearchCriterion;
import com.kodality.termserver.ts.ContactDetail;
import com.kodality.termserver.ts.Language;
import com.kodality.zmei.fhir.datatypes.ContactPoint;
import com.kodality.zmei.fhir.datatypes.Identifier;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

public abstract class BaseFhirMapper {

  public static String[] parseCompositeId(String id) {
    id = URLDecoder.decode(id, StandardCharsets.UTF_8);
    if (!id.contains("@")) {
      return new String[]{id, null};
    }
    String one = StringUtils.substringBefore(id, "@");
    String two = StringUtils.substringAfter(id, "@");
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

  protected static List<Identifier> toFhirIdentifiers(List<com.kodality.commons.model.Identifier> identifiers) {
    if (identifiers == null) {
      return null;
    }
    return identifiers.stream().map(i -> new Identifier().setSystem(i.getSystem()).setValue(i.getValue())).collect(Collectors.toList());
  }

  protected static String toFhirName(LocalizedName name) {
    if (name == null) {
      return null;
    }
    return name.getOrDefault(Language.en, name.values().stream().findFirst().orElse(null));
  }

}
