package com.kodality.termx.terminology.fileimporter.valueset.utils;

import com.kodality.commons.model.Issue;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;


@Getter
@Setter
@Accessors(chain = true)
public class ValueSetFileImportResponse {
  private String diff;
  private List<Issue> errors = new ArrayList<>();
}
