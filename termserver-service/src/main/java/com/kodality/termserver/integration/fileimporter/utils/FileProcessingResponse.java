package com.kodality.termserver.integration.fileimporter.utils;

import com.kodality.termserver.integration.fileimporter.utils.FileProcessingRequest.FileProcessProperty;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;


@Getter
@Setter
@Accessors(chain = true)
public class FileProcessingResponse {
  private List<Map<String, FileProcessingResponseProperty>> entities;

  @Getter
  @Setter
  public static class FileProcessingResponseProperty extends FileProcessProperty {
    private Object value;
  }
}
