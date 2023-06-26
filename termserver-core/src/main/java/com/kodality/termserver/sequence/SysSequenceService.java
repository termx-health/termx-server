package com.kodality.termserver.sequence;

import com.kodality.commons.exception.ApiClientException;
import com.kodality.commons.exception.ApiException;
import com.kodality.commons.model.Issue;
import com.kodality.commons.model.QueryResult;
import com.kodality.commons.sequence.SequenceService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@Singleton
@RequiredArgsConstructor
public class SysSequenceService {
  private final List<String> VALID_TOKENS = List.of("YYYY", "YY", "MM", "DD", "N+", "A+");
  private final SysSequenceRepository sysSequenceRepository;
  private final SequenceService sequenceService;

  public QueryResult<SysSequence> query(SysSequenceQueryParams params) {
    return sysSequenceRepository.query(params);
  }

  public SysSequence load(Long id) {
    return sysSequenceRepository.load(id);
  }

  public Long save(SysSequence sequence) {
    validateCode(sequence);
    validatePattern(sequence.getPattern());
    return sysSequenceRepository.save(sequence);
  }

  public String getNextValue(String code) {
    return sequenceService.getNextValue(code, null, LocalDate.now(), null);
  }


  private void validateCode(SysSequence sequence) {
    if (hasInvalidCharacters(sequence.getCode())) {
      throw new ApiClientException("SEQ104", "Sequence code contains invalid characters");
    }

    if (sequence.getId() != null) {
      SysSequence persisted = load(sequence.getId());
      if (!sequence.getCode().equals(persisted.getCode())) {
        throw new ApiClientException("SEQ103", "Cannot change code");
      }
    }

    if (sysSequenceRepository.hasDuplicate(sequence)) {
      throw new ApiClientException("SEQ101", "Code must be unique");
    }
  }

  private boolean hasInvalidCharacters(String code) {
    return Pattern.compile("[^\\w-]").matcher(code).find();
  }

  private void validatePattern(String pattern) {
    String[] tokens = StringUtils.substringsBetween(pattern, "[", "]");
    if (tokens == null) {
      throw new ApiException(400, Issue.error("SEQ105", "Pattern must contain pattern values"));
    }

    List<String> tokenList = Arrays.asList(tokens);
    List<Issue> errs = new ArrayList<>();

    tokenList.stream()
        .filter(t -> VALID_TOKENS.stream().noneMatch(validToken -> t.matches("^" + validToken + "$")))
        .forEach(t -> errs.add(Issue.error("SEQ102", "Invalid pattern value: {{value}}").setParams(Map.of("value", t))));

    if (!errs.isEmpty()) {
      throw new ApiException(400, errs);
    }
  }
}
