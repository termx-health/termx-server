package com.kodality.termx.terminology.fileimporter.fileparser;

import com.univocity.parsers.common.processor.RowListProcessor;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.List;

public class CsvFileParser implements IFileParser {
    private final RowListProcessor processor;

    public CsvFileParser(byte[] csv) {
        RowListProcessor processor = new RowListProcessor();
        CsvParserSettings settings = new CsvParserSettings();
        settings.setDelimiterDetectionEnabled(true);
        settings.setLineSeparatorDetectionEnabled(true);
        settings.setProcessor(processor);
        settings.setHeaderExtractionEnabled(true);
        new CsvParser(settings).parse(new ByteArrayInputStream(csv));
        this.processor = processor;
    }

    @Override
    public List<String> getHeaders() {
        return Arrays.asList(processor.getHeaders());
    }

    @Override
    public List<String[]> getRows() {
        return processor.getRows();
    }
}
