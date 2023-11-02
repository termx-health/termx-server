package com.kodality.termx.task;

public interface TaskStatus {
  String draft = "draft";
  String requested = "requested";
  String received = "received";
  String accepted = "accepted";
  String rejected = "rejected";
  String ready = "ready";
  String cancelled = "cancelled";
  String in_progress = "in-progress";
  String on_hold = "on-hold";
  String failed = "failed";
  String completed = "completed";
  String error = "entered-in-error";
}
