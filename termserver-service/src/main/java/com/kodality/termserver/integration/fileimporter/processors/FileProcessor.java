package com.kodality.termserver.integration.fileimporter.processors;

import com.kodality.termserver.integration.fileimporter.utils.FileAnalysisResponse;
import com.kodality.termserver.integration.fileimporter.utils.FileProcessingRequest.FileProcessingProperty;
import com.kodality.termserver.integration.fileimporter.utils.FileProcessingResponse;
import com.univocity.parsers.common.processor.RowListProcessor;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import com.univocity.parsers.tsv.TsvParser;
import com.univocity.parsers.tsv.TsvParserSettings;
import java.io.ByteArrayInputStream;
import java.util.List;

public abstract class FileProcessor {
  public static final String DATE = "date";
  public static final String INTEGER = "integer";
  public static final String TEXT = "text";
  public static final String BOOLEAN = "boolean";


  public abstract String getType();

  public abstract FileAnalysisResponse analyze(String type, byte[] file);

  public abstract FileProcessingResponse process(String type, byte[] file, List<FileProcessingProperty> importProperties);


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

  protected RowListProcessor tsvProcessor(byte[] tsv) {
    RowListProcessor processor = new RowListProcessor();
    TsvParserSettings settings = new TsvParserSettings();
    settings.setLineSeparatorDetectionEnabled(true);
    settings.setProcessor(processor);
    settings.setHeaderExtractionEnabled(true);
    new TsvParser(settings).parse(new ByteArrayInputStream(tsv));
    return processor;
  }
}
