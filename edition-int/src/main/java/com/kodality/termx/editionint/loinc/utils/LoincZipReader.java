package com.kodality.termx.editionint.loinc.utils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;


@Slf4j
public class LoincZipReader {
  private static final Map<String, String> fileNameMap = Map.of(
      "Part", "parts",
      "LoincPartLink_Primary", "terminology",
      "LoincPartLink_Supplementary", "supplementary-properties",
      "PanelsAndForms", "panels",
      "AnswerList", "answer-list",
      "LoincAnswerListLink", "answer-list-link",
      "LoincUniversalLabOrdersValueSet", "order-observation");

  public List<Pair<String, byte[]>> handleZipPack(byte[] bytes) {
    List<Pair<String, byte[]>> files = new ArrayList<>();

    log.info("Unpacking LOINC ZIP");
    ZipInputStream zipIn = new ZipInputStream(new ByteArrayInputStream(bytes));
    ZipEntry entry;
    try {
      while ((entry = zipIn.getNextEntry()) != null) {
        System.out.println(entry.getName());
        if (fileNameMap.containsKey(entry.getName())) {
          files.add(Pair.of(fileNameMap.get(entry.getName()), IOUtils.toByteArray(zipIn)));
        }
        zipIn.closeEntry();
      }

    } catch (IOException | RuntimeException e) {
      log.error("Error while reading LOINC pack", e);
      throw new RuntimeException(e);
    }
    return files;
  }
}
