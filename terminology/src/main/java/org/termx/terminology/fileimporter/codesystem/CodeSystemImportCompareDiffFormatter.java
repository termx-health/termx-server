package org.termx.terminology.fileimporter.codesystem;

import com.kodality.commons.util.PipeUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.lang3.StringUtils;
import org.termx.ts.codesystem.CodeSystemCompareResult;
import org.termx.ts.codesystem.CodeSystemCompareResult.CodeSystemCompareResultDiffItem;

import static java.util.stream.Collectors.groupingBy;

/**
 * Formats {@link CodeSystemCompareResult} as a human-readable diff for import dry-run / validation UI.
 */
public final class CodeSystemImportCompareDiffFormatter {

  private CodeSystemImportCompareDiffFormatter() {}

  public static String composeCompareSummary(CodeSystemCompareResult res) {
    List<String> msg = new ArrayList<>();
    msg.add("##### Created ######");
    res.getAdded().forEach(d -> msg.add(" * %s".formatted(d)));

    msg.add("##### Changed ######");
    res.getChanged().forEach(c -> {
      CodeSystemCompareResultDiffItem old = c.getDiff().getOld();
      CodeSystemCompareResultDiffItem mew = c.getDiff().getMew();

      List<String> changes = new ArrayList<>();
      changes.add(compareDiffElements("status", old.getStatus(), mew.getStatus()));
      changes.add(compareDiffElements("description", old.getDescription(), mew.getDescription()));
      changes.add(compareDiffElements(old.getDesignations(), mew.getDesignations()));
      changes.add(compareDiffElements(old.getProperties(), mew.getProperties()));

      if (changes.stream().anyMatch(StringUtils::isNotBlank)) {
        msg.add(" * %s".formatted(c.getCode()));
        changes.stream().filter(StringUtils::isNotBlank).forEach(msg::add);
      }
    });

    msg.add("##### Deleted ######");
    res.getDeleted().forEach(d -> msg.add(" * %s".formatted(d)));

    return StringUtils.join(msg, "\n");
  }

  private static String compareDiffElements(String key, String el1, String el2) {
    return !Objects.equals(el1, el2) ? "\t- %s: \"%s\" -> \"%s\"".formatted(key, el1, el2) : null;
  }

  private static String compareDiffElements(List<String> els1, List<String> els2) {
    Map<String, List<String>> collect1 = els1 == null ? Map.of() : els1.stream()
        .map(PipeUtil::parsePipe)
        .collect(groupingBy(s -> s[0], Collectors.mapping(s -> s[1], Collectors.toList())));
    Map<String, List<String>> collect2 = els2 == null ? Map.of() : els2.stream()
        .map(PipeUtil::parsePipe)
        .collect(groupingBy(s -> s[0], Collectors.mapping(s -> s[1], Collectors.toList())));

    return SetUtils.union(collect1.keySet(), collect2.keySet())
        .stream()
        .map(k -> {
          List<String> v1 = collect1.getOrDefault(k, List.of());
          List<String> v2 = collect2.getOrDefault(k, List.of());
          return ListUtils.isEqualList(v1, v2) ? null : "\t- %s: \"%s\" -> \"%s\"".formatted(k, StringUtils.join(v1, ", "), StringUtils.join(v2, ", "));
        })
        .filter(Objects::nonNull)
        .collect(Collectors.joining("\n"));
  }
}
