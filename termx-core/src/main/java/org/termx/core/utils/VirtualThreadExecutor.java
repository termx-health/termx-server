package org.termx.core.utils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VirtualThreadExecutor {
  private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

  public static ExecutorService get() {
    return EXECUTOR;
  }

  private VirtualThreadExecutor() {
  }
}
