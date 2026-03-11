package com.kodality.commons.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class AsyncHelper {
  private final List<CompletableFuture<?>> futures = new ArrayList<>();

  public static AsyncHelper allOf(CompletableFuture<?>... futures) {
    AsyncHelper a = new AsyncHelper();
    Stream.of(futures).forEach(a::add);
    return a;
  }

  public void add(CompletableFuture<?> future) {
    futures.add(future);
  }

  public void add(List<CompletableFuture<?>> futures) {
    this.futures.addAll(futures);
  }

  public void run(Runnable runnable) {
    futures.add(CompletableFuture.runAsync(runnable));
  }

  public void joinAll() {
    joinAll(futures.stream());
  }

  public static void joinAll(Stream<CompletableFuture<?>> stream) {
    CompletableFuture.allOf(stream.filter(Objects::nonNull).toArray(CompletableFuture[]::new)).join();
  }

}
