package com.kodality.termx.core.utils;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.univocity.parsers.csv.CsvWriter;
import com.univocity.parsers.csv.CsvWriterSettings;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.List;

public class CsvUtil {
  public static CsvMapper getMapper() {
    return (CsvMapper) new CsvMapper()
        .enable(CsvParser.Feature.SKIP_EMPTY_LINES)
        .enable(CsvParser.Feature.TRIM_SPACES)
        .enable(CsvParser.Feature.INSERT_NULLS_FOR_MISSING_COLUMNS)
        .enable(JsonGenerator.Feature.IGNORE_UNKNOWN)
        .registerModule(new JavaTimeModule());
  }

  public static byte[] composeCsv(List<String> headers, List<Object[]> rows) {
    OutputStream out = new ByteArrayOutputStream();
    CsvWriterSettings settings = new CsvWriterSettings();
    settings.getFormat().setDelimiter(',');
    CsvWriter writer = new CsvWriter(out, settings);
    writer.writeHeaders(headers);
    writer.writeRowsAndClose(rows);
    return out.toString().getBytes();
  }
}
