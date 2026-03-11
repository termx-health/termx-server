package com.kodality.commons.csv.parser;

import com.kodality.commons.csv.record.CsvRecordList;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;

import java.io.InputStream;

public class CsvReader {
  private final CsvParserSettings settings;

  public CsvReader() {
    settings = new CsvParserSettings();
    settings.getFormat().setDelimiter(';');
    settings.setLineSeparatorDetectionEnabled(true);
    settings.setHeaderExtractionEnabled(true);
  }

  public CsvReader(CsvParserSettings settings) {
    this.settings = settings;
  }

  public CsvRecordList parse(InputStream csvInputStream) {
    CsvRecordProcessor processor = new CsvRecordProcessor();
    settings.setProcessor(processor);
    new CsvParser(settings).parse(csvInputStream);
    return new CsvRecordList(processor.getHeaderList(), processor.getRecords());
  }

  public CsvReader setDelimiter(char delimiter) {
    settings.getFormat().setDelimiter(delimiter);
    return this;
  }

}
