package com.kodality.commons.db.transaction;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
public class TransactionManager {
  public static void rollback() {
    TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
  }

  public static void runAfterCommit(Runnable job) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      CompletableFuture.runAsync(job);
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(new AfterCommitRunner(job));
  }

  public static class AfterCommitRunner extends TransactionSynchronizationAdapter {
    private final Runnable job;

    public AfterCommitRunner(Runnable job) {
      this.job = job;
    }

    @Override
    public void afterCommit() {
      ExecutorService ex = Executors.newFixedThreadPool(1);
      CompletableFuture.runAsync(job, ex).exceptionally(e -> {
        log.error("exception in after commit runner", e);
        return null;
      }).thenRun(() -> ex.shutdown());
    }
  }
}
