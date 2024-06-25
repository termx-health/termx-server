package com.kodality.termx.core.sys.lorque;

import com.kodality.termx.sys.ExecutionStatus;
import com.kodality.termx.sys.lorque.LorqueProcess;
import com.kodality.termx.sys.lorque.ProcessResult;
import com.kodality.termx.sys.lorque.ProcessResultType;
import java.time.LocalDateTime;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class LorqueProcessService {

  private final LorqueProcessRepository repository;

  @Transactional
  public LorqueProcess start(LorqueProcess process) {
    process.setStarted(LocalDateTime.now()).setStatus(ExecutionStatus.RUNNING);
    return process.setId(repository.save(process));
  }

  public LorqueProcess load(Long id) {
    LorqueProcess process = repository.load(id);
    return decorate(process);
  }

  private LorqueProcess decorate(LorqueProcess process) {
    if (process.getResult() != null && ProcessResultType.text.equals(process.getResultType())) {
      process.setResultText(new String(process.getResult()));
    }
    return process;
  }

  public String getStatus(Long id) {
    return repository.getStatus(id);
  }

  @Transactional
  public LorqueProcess complete(Long id, ProcessResult result) {
    LorqueProcess process = load(id);
    process.setStatus(ExecutionStatus.COMPLETED);
    return process(process, result);
  }

  @Transactional
  public LorqueProcess fail(Long id, ProcessResult result) {
    LorqueProcess process = load(id);
    process.setStatus(ExecutionStatus.FAILED);
    return process(process, result);
  }

  @Transactional
  public LorqueProcess process(LorqueProcess process, ProcessResult result) {
    process.setFinished(LocalDateTime.now());
    process.setResult(result.getContent());
    process.setResultType(result.getContentType());
    repository.save(process);
    return process;
  }

  public void cleanup(int days) {
    repository.cleanup(days);
  }
}

