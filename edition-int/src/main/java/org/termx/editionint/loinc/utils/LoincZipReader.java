package org.termx.editionint.loinc.utils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;


/**
 * Walks a LOINC release zip and extracts the CSV files the {@code LoincService} pipeline knows
 * about, keyed by the import-step name. Matching is by file <em>basename</em> (case-insensitive,
 * any path separator normalised) so both modern releases (where CSVs live under
 * {@code AccessoryFiles/…/Part.csv}, {@code LoincTable/…}, etc.) and older flat layouts work.
 *
 * <p>Translation files use a per-language naming convention ({@code <lang>LinguisticVariant.csv})
 * — pass the requested language to {@link #unpack(InputStream, String)} so only that variant is
 * extracted. {@code null} or empty {@code language} skips translation entries.</p>
 */
@Slf4j
public class LoincZipReader {
  private static final Map<String, String> BASENAME_TO_KEY = Map.of(
      "Part", "parts",
      "LoincPartLink_Primary", "terminology",
      "LoincPartLink_Supplementary", "supplementary-properties",
      "PanelsAndForms", "panels",
      "AnswerList", "answer-list",
      "LoincAnswerListLink", "answer-list-link",
      "LoincUniversalLabOrdersValueSet", "order-observation");

  /**
   * Legacy entry point — buffers the whole zip in memory. New callers should prefer
   * {@link #unpack(InputStream, String)} with a {@code FileInputStream} from a local temp file.
   */
  public List<Pair<String, byte[]>> handleZipPack(byte[] bytes) {
    return unpack(new ByteArrayInputStream(bytes), null);
  }

  public List<Pair<String, byte[]>> unpack(InputStream zipStream, String language) {
    List<Pair<String, byte[]>> files = new ArrayList<>();
    String translationsName = (language == null || language.isBlank()) ? null
        : (language.toLowerCase() + "LinguisticVariant");

    log.info("Unpacking LOINC ZIP (lang={})", language);
    try (ZipInputStream zipIn = new ZipInputStream(zipStream)) {
      ZipEntry entry;
      while ((entry = zipIn.getNextEntry()) != null) {
        if (entry.isDirectory()) {
          zipIn.closeEntry();
          continue;
        }
        String key = matchKey(entry.getName(), translationsName);
        if (key != null) {
          files.add(Pair.of(key, IOUtils.toByteArray(zipIn)));
        }
        zipIn.closeEntry();
      }
    } catch (IOException | RuntimeException e) {
      log.error("Error while reading LOINC pack", e);
      throw new RuntimeException(e);
    }
    return files;
  }

  private static String matchKey(String entryName, String translationsBaseName) {
    String normalised = entryName.replace('\\', '/');
    int slash = normalised.lastIndexOf('/');
    String basename = slash < 0 ? normalised : normalised.substring(slash + 1);
    int dot = basename.lastIndexOf('.');
    if (dot >= 0) {
      // Only match .csv (or no-extension) entries — Loinc release zips also ship .txt READMEs,
      // .xml, .docx etc. that we never want to feed to the CSV parser.
      String ext = basename.substring(dot + 1).toLowerCase();
      if (!"csv".equals(ext)) {
        return null;
      }
      basename = basename.substring(0, dot);
    }
    // Case-insensitive lookup against the small static map.
    for (Map.Entry<String, String> e : BASENAME_TO_KEY.entrySet()) {
      if (e.getKey().equalsIgnoreCase(basename)) {
        return e.getValue();
      }
    }
    if (translationsBaseName != null && translationsBaseName.equalsIgnoreCase(basename)) {
      return "translations";
    }
    return null;
  }
}
