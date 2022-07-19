package com.kodality.termserver.integration.fileimporter.processors;

import com.kodality.termserver.integration.fileimporter.utils.FileAnalysisResponse;
import com.kodality.termserver.integration.fileimporter.utils.FileProcessingRequest.FileProcessProperty;
import com.kodality.termserver.integration.fileimporter.utils.FileProcessingResponse;
import com.univocity.parsers.common.processor.RowListProcessor;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import com.univocity.parsers.tsv.TsvParser;
import com.univocity.parsers.tsv.TsvParserSettings;
import java.io.ByteArrayInputStream;
import java.util.List;

public abstract class FileProcessor {
  public static final String IDENTIFIER = "identifier";
  public static final String ALIAS = "alias";
  public static final String DISPLAY = "display";
  public static final String DESIGNATION = "designation";
  public static final String PARENT = "parent";
  public static final String LEVEL = "level";
  public static final String VALID_FROM = "validFrom";
  public static final String VALID_TO = "validTo";
  public static final String STATUS = "status";
  public static final String DESCRIPTION = "description";
  public static final String MODIFIED_AT = "modifiedAt";


  public abstract String getType();

  public abstract FileAnalysisResponse analyze(String type, byte[] file);

  public abstract FileProcessingResponse process(String type, byte[] file, List<FileProcessProperty> importProperties);


  protected RowListProcessor csvProcessor(byte[] csv) {
    RowListProcessor processor = new RowListProcessor();
    CsvParserSettings settings = new CsvParserSettings();
    settings.setDelimiterDetectionEnabled(true);
    settings.setLineSeparatorDetectionEnabled(true);
    settings.setProcessor(processor);
    settings.setHeaderExtractionEnabled(true);
    new CsvParser(settings).parse(new ByteArrayInputStream(csv));
    return processor;
  }

  protected RowListProcessor tsvProcessor(byte[] csv) {
    RowListProcessor processor = new RowListProcessor();
    TsvParserSettings settings = new TsvParserSettings();
    settings.setLineSeparatorDetectionEnabled(true);
    settings.setProcessor(processor);
    settings.setHeaderExtractionEnabled(true);
    new TsvParser(settings).parse(new ByteArrayInputStream(csv));
    return processor;
  }
}
