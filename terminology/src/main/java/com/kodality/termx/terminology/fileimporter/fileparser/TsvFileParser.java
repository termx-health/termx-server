package com.kodality.termx.terminology.fileimporter.fileparser;

import com.univocity.parsers.common.processor.RowListProcessor;
import com.univocity.parsers.tsv.TsvParser;
import com.univocity.parsers.tsv.TsvParserSettings;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.List;

public class TsvFileParser implements IFileParser {
    private final RowListProcessor processor;

    public TsvFileParser(byte[] tsv) {
        RowListProcessor processor = new RowListProcessor();
        TsvParserSettings settings = new TsvParserSettings();
        settings.setLineSeparatorDetectionEnabled(true);
        settings.setProcessor(processor);
        settings.setHeaderExtractionEnabled(true);
        new TsvParser(settings).parse(new ByteArrayInputStream(tsv));
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
