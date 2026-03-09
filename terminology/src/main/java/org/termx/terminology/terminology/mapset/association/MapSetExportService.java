package org.termx.terminology.terminology.mapset.association;

import com.kodality.commons.util.JsonUtil;
import org.termx.core.sys.lorque.LorqueProcessService;
import org.termx.core.utils.CsvUtil;
import org.termx.core.utils.XlsxUtil;
import org.termx.sys.lorque.LorqueProcess;
import org.termx.sys.lorque.ProcessResult;
import org.termx.terminology.ApiError;
import org.termx.terminology.terminology.mapset.version.MapSetVersionService;
import org.termx.ts.mapset.MapSetAssociation;
import org.termx.ts.mapset.MapSetAssociationQueryParams;
import org.termx.ts.mapset.MapSetPropertyValue;
import org.termx.ts.mapset.MapSetVersion;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class MapSetExportService {
  private final LorqueProcessService lorqueProcessService;
  private final MapSetAssociationService mapSetAssociationService;
  private final MapSetVersionService mapSetVersionService;

  private static final String process = "ms-association-export";

  public LorqueProcess export(String mapSet, String version, String format) {
    Map<String, String> params = Map.of("mapSet", mapSet, "version", version, "format", format);
    return lorqueProcessService.run(process, params, this::composeResult);
  }

  private ProcessResult composeResult(Map<String, String> params) {
    String mapSetId = params.get("mapSet");
    String versionNumber = params.get("version");
    
    MapSetVersion mapSetVersion = mapSetVersionService.load(mapSetId, versionNumber).orElseThrow();
    
    MapSetAssociationQueryParams queryParams = new MapSetAssociationQueryParams()
        .setMapSet(mapSetId)
        .setMapSetVersionId(mapSetVersion.getId())
        .all();
    
    List<MapSetAssociation> associations = mapSetAssociationService.query(queryParams).getData();

    List<String> headers = composeHeaders(associations);
    List<Object[]> rows = associations.stream().map(a -> composeRow(a, headers)).toList();

    String format = params.get("format");
    if ("csv".equals(format)) {
      return ProcessResult.binary(CsvUtil.composeCsv(headers, rows, ",").toString().getBytes());
    }
    if ("xlsx".equals(format)) {
      return ProcessResult.binary(XlsxUtil.composeXlsx(headers, rows, "associations"));
    }
    throw ApiError.TE807.toApiException();
  }

  private List<String> composeHeaders(List<MapSetAssociation> associations) {
    List<String> fields = new ArrayList<>();
    
    fields.add("sourceCode");
    fields.add("sourceCodeSystem");
    fields.add("sourceDisplay");
    fields.add("targetCode");
    fields.add("targetCodeSystem");
    fields.add("targetDisplay");
    fields.add("relationship");
    fields.add("verified");
    fields.add("noMap");

    Set<String> propertyNames = new TreeSet<>();
    
    int limit = Math.min(associations.size(), 1000);
    for (int i = 0; i < limit; i++) {
      MapSetAssociation association = associations.get(i);
      Optional.ofNullable(association.getPropertyValues()).orElse(List.of()).forEach(pv ->
          propertyNames.add(pv.getMapSetPropertyName())
      );
    }
    
    fields.addAll(propertyNames);
    
    return fields;
  }

  private Object[] composeRow(MapSetAssociation association, List<String> headers) {
    List<Object> row = new ArrayList<>();
    
    Map<String, List<MapSetPropertyValue>> propertyValueMap = Optional.ofNullable(association.getPropertyValues())
        .orElse(List.of()).stream()
        .collect(Collectors.groupingBy(MapSetPropertyValue::getMapSetPropertyName));

    for (String header : headers) {
      switch (header) {
        case "sourceCode":
          row.add(association.getSource() != null ? association.getSource().getCode() : "");
          break;
        case "sourceCodeSystem":
          row.add(association.getSource() != null ? association.getSource().getCodeSystem() : "");
          break;
        case "sourceDisplay":
          row.add(association.getSource() != null ? association.getSource().getDisplay() : "");
          break;
        case "targetCode":
          row.add(association.getTarget() != null ? association.getTarget().getCode() : "");
          break;
        case "targetCodeSystem":
          row.add(association.getTarget() != null ? association.getTarget().getCodeSystem() : "");
          break;
        case "targetDisplay":
          row.add(association.getTarget() != null ? association.getTarget().getDisplay() : "");
          break;
        case "relationship":
          row.add(association.getRelationship() != null ? association.getRelationship() : "");
          break;
        case "verified":
          row.add(association.isVerified());
          break;
        case "noMap":
          row.add(association.isNoMap());
          break;
        default:
          if (propertyValueMap.containsKey(header)) {
            String value = propertyValueMap.get(header).stream()
                .map(pv -> pv.getValue() != null ? (pv.getValue() instanceof String ? (String) pv.getValue() : JsonUtil.toJson(pv.getValue())) : "")
                .collect(Collectors.joining("#"));
            row.add(value);
          } else {
            row.add("");
          }
          break;
      }
    }
    
    return row.toArray();
  }
}
