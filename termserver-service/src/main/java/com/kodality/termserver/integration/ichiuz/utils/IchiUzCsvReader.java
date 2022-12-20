package com.kodality.termserver.integration.ichiuz.utils;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.kodality.termserver.common.utils.CsvMapperUtil;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IchiUzCsvReader {
  private static final char SEPARATOR = ',';

  public static List<IchiUz> read(byte[] bytes) {
    try {
      CsvSchema schema = CsvSchema.emptySchema()
          .withColumnSeparator(SEPARATOR)
          .withHeader();

      MappingIterator<IchiUz> it = CsvMapperUtil.getMapper()
          .readerFor(IchiUz.class)
          .with(schema)
          .readValues(new String(bytes, StandardCharsets.ISO_8859_1));

      return it.readAll();
    } catch (IOException e) {
      log.error("Error occurred while reading ICHI uz CSV file", e);
      return List.of();
    }
  }
}
