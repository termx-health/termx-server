package com.kodality.termx.atcest.utils;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.kodality.termx.utils.CsvMapperUtil;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AtcEstCsvReader {

  private static final char SEPARATOR = ';';

  public static List<AtcEst> read(byte[] bytes) {
    try {
      CsvSchema schema = CsvSchema.emptySchema()
          .withColumnSeparator(SEPARATOR)
          .withHeader();

      MappingIterator<AtcEst> it = CsvMapperUtil.getMapper()
          .readerFor(AtcEst.class)
          .with(schema)
          .readValues(new String(bytes, StandardCharsets.ISO_8859_1));

      return it.readAll();
    } catch (IOException e) {
      log.error("Error occurred while reading ATC CSV file", e);
      return List.of();
    }
  }

}
