package com.kodality.termserver.job;

public interface JobExecutionStatus {
  String RUNNING = "running";
  String COMPLETED = "completed";
  String WARNINGS = "warnings";
  String FAILED = "failed";
}
