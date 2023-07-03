package com.kodality.termx.fileimporter.association.utils;

import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

public class AssociationFileImportRowMapper {

  public static List<AssociationFileImportRow> getAssociationFileImportRows(AssociationFileImportRequest req, List<String> headers, List<String[]> rows) {
    return rows.stream().flatMap(r -> {
      String target = r[headers.indexOf(req.getTargetColumn())];
      String source = r[headers.indexOf(req.getSourceColumn())];
      List<String> sources = Arrays.stream(StringUtils.split(source, req.getSourceColumnSeparator() != null ? req.getSourceColumnSeparator() : ",")).map(String::trim).toList();
      String order = r[headers.indexOf(req.getOrderColumn())];

      return sources.stream().map(s -> {
        var row = new AssociationFileImportRow();
        row.setTarget(target);
        row.setSource(s);
        if (StringUtils.isNotBlank(order)) {
          row.setOrder(Integer.valueOf(order));
        }
        return row;
      });
    }).toList();
  }
}
