package com.kodality.termx.terminology.fileimporter.fileparser;

import java.io.File;

public class FileParserFactory {
    public static IFileParser getParser(String type, byte[] file) {
        return switch (type) {
            case "csv" -> new CsvFileParser(file);
            case "tsv" -> new TsvFileParser(file);
            //case "xlsx" -> new XlsxFileParser(file);
            default -> throw new IllegalArgumentException("Unsupported file type: " + type);
        };
    }
}
