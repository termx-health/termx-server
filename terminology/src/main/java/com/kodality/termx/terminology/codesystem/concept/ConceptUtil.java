package com.kodality.termx.terminology.codesystem.concept;

import com.kodality.termx.ts.codesystem.Designation;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import java.util.List;

public class ConceptUtil {
  private static final String DISPLAY = "display";

  public static Designation getDisplay(List<Designation> designations, String preferredLang, List<String> otherLang) {
    List<Designation> displays = designations.stream().filter(d -> DISPLAY.equals(d.getDesignationType())).toList();
    return displays.stream().filter(d -> StringUtils.isNotEmpty(preferredLang) && d.getLanguage() != null && d.getLanguage().equals(preferredLang))
        .findFirst().orElse(displays.stream()
            .filter(d -> StringUtils.isNotEmpty(preferredLang) && d.getLanguage() != null && d.getLanguage().startsWith(preferredLang))
            .findFirst().orElse(displays.stream()
                .filter(d -> CollectionUtils.isEmpty(otherLang) ||
                    d.getLanguage() != null && otherLang.stream().anyMatch(pl -> d.getLanguage().equals(pl)))
                .findFirst().orElse(displays.stream()
                    .filter(d -> CollectionUtils.isEmpty(otherLang) ||
                        d.getLanguage() != null && otherLang.stream().anyMatch(pl -> d.getLanguage().startsWith(pl)))
                    .findFirst().orElse(null))));
  }
}
