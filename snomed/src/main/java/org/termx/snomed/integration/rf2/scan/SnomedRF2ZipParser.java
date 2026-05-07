package org.termx.snomed.integration.rf2.scan;

import jakarta.inject.Singleton;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class SnomedRF2ZipParser {

  public ParsedRF2 parse(byte[] zipBytes) throws IOException {
    return parse(zipBytes, null, true);
  }

  public ParsedRF2 parse(byte[] zipBytes, java.util.function.Consumer<String> phaseReporter) throws IOException {
    return parse(zipBytes, phaseReporter, true);
  }

  /**
   * Parse the RF2 zip, optionally restricted to the lightweight set of files for "summary" mode.
   * When {@code fullMode} is false, relationship and language-refset entries are skipped — those
   * two files dominate the wall-clock time on a full International edition zip.
   */
  public ParsedRF2 parse(byte[] zipBytes, java.util.function.Consumer<String> phaseReporter, boolean fullMode) throws IOException {
    ParsedRF2 parsed = new ParsedRF2();
    try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        if (entry.isDirectory()) {
          continue;
        }
        String name = stripPath(entry.getName());
        try {
          if (name.startsWith("sct2_Concept_")) {
            report(phaseReporter, "concepts");
            parsed.getConcepts().addAll(readConcepts(zis));
          } else if (name.startsWith("sct2_Description_")) {
            report(phaseReporter, "descriptions");
            parsed.getDescriptions().addAll(readDescriptions(zis, false));
          } else if (name.startsWith("sct2_TextDefinition_")) {
            report(phaseReporter, "text-definitions");
            parsed.getDescriptions().addAll(readDescriptions(zis, true));
          } else if (fullMode && (name.startsWith("sct2_Relationship_") || name.startsWith("sct2_StatedRelationship_"))) {
            report(phaseReporter, "relationships");
            parsed.getRelationships().addAll(readRelationships(zis));
          } else if (fullMode && name.startsWith("der2_cRefset_Language")) {
            report(phaseReporter, "language-refset");
            parsed.getLanguageRefset().addAll(readLanguageRefset(zis));
          }
        } catch (Exception e) {
          log.warn("Failed to parse RF2 entry {}: {}", name, e.getMessage());
        }
      }
    }
    return parsed;
  }

  private static void report(java.util.function.Consumer<String> reporter, String phase) {
    if (reporter != null) {
      reporter.accept(phase);
    }
  }

  private static String stripPath(String entryName) {
    int slash = entryName.lastIndexOf('/');
    return slash < 0 ? entryName : entryName.substring(slash + 1);
  }

  private List<ConceptRow> readConcepts(ZipInputStream zis) throws IOException {
    List<ConceptRow> rows = new ArrayList<>();
    forEachLine(zis, parts -> {
      if (parts.length < 5) {
        return;
      }
      rows.add(new ConceptRow()
          .setId(parts[0])
          .setEffectiveTime(parts[1])
          .setActive("1".equals(parts[2]))
          .setModuleId(parts[3])
          .setDefinitionStatusId(parts[4]));
    });
    return rows;
  }

  private List<DescriptionRow> readDescriptions(ZipInputStream zis, boolean textDefinition) throws IOException {
    List<DescriptionRow> rows = new ArrayList<>();
    forEachLine(zis, parts -> {
      if (parts.length < 9) {
        return;
      }
      rows.add(new DescriptionRow()
          .setId(parts[0])
          .setEffectiveTime(parts[1])
          .setActive("1".equals(parts[2]))
          .setModuleId(parts[3])
          .setConceptId(parts[4])
          .setLanguageCode(parts[5])
          .setTypeId(parts[6])
          .setTerm(parts[7])
          .setCaseSignificanceId(parts[8])
          .setTextDefinition(textDefinition));
    });
    return rows;
  }

  private List<RelationshipRow> readRelationships(ZipInputStream zis) throws IOException {
    List<RelationshipRow> rows = new ArrayList<>();
    forEachLine(zis, parts -> {
      if (parts.length < 10) {
        return;
      }
      Integer group = null;
      try {
        group = Integer.parseInt(parts[6]);
      } catch (NumberFormatException ignored) {
      }
      rows.add(new RelationshipRow()
          .setId(parts[0])
          .setEffectiveTime(parts[1])
          .setActive("1".equals(parts[2]))
          .setModuleId(parts[3])
          .setSourceId(parts[4])
          .setDestinationId(parts[5])
          .setRelationshipGroup(group)
          .setTypeId(parts[7])
          .setCharacteristicTypeId(parts[8])
          .setModifierId(parts[9]));
    });
    return rows;
  }

  private List<LanguageRefsetRow> readLanguageRefset(ZipInputStream zis) throws IOException {
    List<LanguageRefsetRow> rows = new ArrayList<>();
    forEachLine(zis, parts -> {
      if (parts.length < 7) {
        return;
      }
      rows.add(new LanguageRefsetRow()
          .setId(parts[0])
          .setEffectiveTime(parts[1])
          .setActive("1".equals(parts[2]))
          .setModuleId(parts[3])
          .setRefsetId(parts[4])
          .setReferencedComponentId(parts[5])
          .setAcceptabilityId(parts[6]));
    });
    return rows;
  }

  private void forEachLine(ZipInputStream zis, java.util.function.Consumer<String[]> handler) throws IOException {
    BufferedReader reader = new BufferedReader(new InputStreamReader(zis, StandardCharsets.UTF_8));
    boolean header = true;
    String line;
    while ((line = reader.readLine()) != null) {
      if (header) {
        header = false;
        continue;
      }
      if (line.isEmpty()) {
        continue;
      }
      handler.accept(line.split("\t", -1));
    }
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ParsedRF2 {
    private List<ConceptRow> concepts = new ArrayList<>();
    private List<DescriptionRow> descriptions = new ArrayList<>();
    private List<RelationshipRow> relationships = new ArrayList<>();
    private List<LanguageRefsetRow> languageRefset = new ArrayList<>();
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class ConceptRow {
    private String id;
    private String effectiveTime;
    private boolean active;
    private String moduleId;
    private String definitionStatusId;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class DescriptionRow {
    private String id;
    private String effectiveTime;
    private boolean active;
    private String moduleId;
    private String conceptId;
    private String languageCode;
    private String typeId;
    private String term;
    private String caseSignificanceId;
    private boolean textDefinition;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class RelationshipRow {
    private String id;
    private String effectiveTime;
    private boolean active;
    private String moduleId;
    private String sourceId;
    private String destinationId;
    private Integer relationshipGroup;
    private String typeId;
    private String characteristicTypeId;
    private String modifierId;
  }

  @Getter
  @Setter
  @Accessors(chain = true)
  public static class LanguageRefsetRow {
    private String id;
    private String effectiveTime;
    private boolean active;
    private String moduleId;
    private String refsetId;
    private String referencedComponentId;
    private String acceptabilityId;
  }
}
