package com.kodality.termx.fileimporter.codesystem.utils;

import com.kodality.commons.model.Issue;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;


@Getter
@Setter
@Accessors(chain = true)
public class CodeSystemFileImportResponse {
  private String diff;
  private List<Issue> errors = new ArrayList<>();
}
