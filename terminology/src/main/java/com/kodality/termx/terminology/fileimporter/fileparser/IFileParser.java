package com.kodality.termx.terminology.fileimporter.fileparser;

import java.util.List;

public interface IFileParser {
    List<String> getHeaders();
    List<String[]> getRows();
}
