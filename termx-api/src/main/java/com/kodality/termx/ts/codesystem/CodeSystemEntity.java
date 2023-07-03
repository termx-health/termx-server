package com.kodality.termx.ts.codesystem;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public abstract class CodeSystemEntity {
  private Long id;
  private String type;
  private String codeSystem;

  private List<CodeSystemEntityVersion> versions;
}
