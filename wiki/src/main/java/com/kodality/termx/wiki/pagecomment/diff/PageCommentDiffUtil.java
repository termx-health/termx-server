package com.kodality.termx.wiki.pagecomment.diff;

import com.kodality.termx.wiki.pagecomment.diff.diff_match_patch.Diff;
import com.kodality.termx.wiki.pagecomment.diff.diff_match_patch.Operation;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

public class PageCommentDiffUtil {
  private static List<Diff> getChanges(String original, String modified) {
    diff_match_patch dmp = new diff_match_patch();
    return dmp.diff_main(original, modified);
  }

  public static Integer recalculateLineNumber(int lineNumber, String before, String after) {
    String NEW_LINE = "\n";

    List<Diff> changes = getChanges(before, after);
    List<Diff> newLineChanges = changes.stream().filter(c -> c.text.contains(NEW_LINE)).toList();

    int lr = 0;
    int newLineNr = lineNumber;

    for (Diff change : newLineChanges) {
      if (lr >= lineNumber) {
        return newLineNr;
      }
      if (change.text.contains(NEW_LINE)) {
        int shift = StringUtils.countMatches(change.text, NEW_LINE);
        if (change.operation.equals(Operation.INSERT)) {
          newLineNr += shift;
          lineNumber += shift;
        } else if (change.operation.equals(Operation.DELETE)) {
          newLineNr -= shift;
        }
        lr += shift;
      }
    }
    return newLineNr;
  }
}
