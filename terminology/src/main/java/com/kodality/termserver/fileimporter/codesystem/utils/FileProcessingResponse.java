package com.kodality.termserver.fileimporter.codesystem.utils;

import com.kodality.commons.model.Issue;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;


@Getter
@Setter
@Accessors(chain = true)
public class FileProcessingResponse {
  private String diff;
  private List<Issue> errors;
}
