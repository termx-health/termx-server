package com.kodality.termserver.common.utils;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class CsvMapperUtil {
  public static CsvMapper getMapper() {
    return (CsvMapper) new CsvMapper()
        .enable(CsvParser.Feature.SKIP_EMPTY_LINES)
        .enable(CsvParser.Feature.TRIM_SPACES)
        .enable(CsvParser.Feature.INSERT_NULLS_FOR_MISSING_COLUMNS)
        .enable(JsonGenerator.Feature.IGNORE_UNKNOWN)
        .registerModule(new JavaTimeModule());
  }
}
