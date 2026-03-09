package org.termx.core.sys.checklist.validaton;

import org.termx.sys.checklist.Checklist.ChecklistWhitelist;
import org.termx.sys.checklist.ChecklistAssertion.ChecklistAssertionError;
import org.termx.ts.codesystem.CodeSystem;
import org.termx.ts.codesystem.Concept;
import java.util.List;

public interface CodeSystemRuleValidator {

  String getRuleCode();
  List<ChecklistAssertionError> validate(CodeSystem codeSystem, List<Concept> concepts, List<ChecklistWhitelist> whitelists);
}
