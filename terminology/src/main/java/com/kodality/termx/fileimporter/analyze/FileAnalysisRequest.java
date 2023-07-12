package com.kodality.termx.fileimporter.analyze;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Introspected
public class FileAnalysisRequest {
  private String link;
  private String type; // csv; tsv
}