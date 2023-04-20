package com.kodality.termserver.loinc.utils.answerlist;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.tuple.Pair;

@Getter
@Setter
@Accessors(chain = true)
public class LoincAnswerList {
  private String code;
  private String display;
  private String oid;
  private String answerCode;
  private List<Pair<String, Integer>> answerLists;
}
