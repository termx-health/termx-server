package com.kodality.termserver.job.logger;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Setter
@Getter
@Accessors(chain = true)
public class ImportLog {
  private List<String> successes;
  private List<String> warnings;
  private List<String> errors;
}
