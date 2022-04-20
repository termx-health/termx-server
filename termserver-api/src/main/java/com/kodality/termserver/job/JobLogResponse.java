package com.kodality.termserver.job;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class JobLogResponse {
  private Long jobId;

  public JobLogResponse() {
  }

  public JobLogResponse(Long jobId) {
    this.jobId = jobId;
  }
}
