package org.termx.terminology.terminology.codesystem.concept;

import com.kodality.commons.util.JsonUtil;
import org.termx.core.auth.SessionStore;
import org.termx.core.sys.lorque.LorqueProcessService;
import org.termx.core.utils.CsvUtil;
import org.termx.core.utils.VirtualThreadExecutor;
import org.termx.core.utils.XlsxUtil;
import org.termx.sys.lorque.LorqueProcess;
import org.termx.sys.lorque.ProcessResult;
import org.termx.terminology.ApiError;
import org.termx.terminology.terminology.codesystem.CodeSystemService;
import org.termx.ts.PublicationStatus;
import org.termx.ts.codesystem.CodeSystem;
import org.termx.ts.codesystem.CodeSystemEntityVersion;
import org.termx.ts.codesystem.Concept;
import org.termx.ts.codesystem.ConceptQueryParams;
import org.termx.ts.codesystem.Designation;
import org.termx.ts.codesystem.EntityProperty;
import org.termx.ts.codesystem.EntityPropertyType;
import org.termx.ts.codesystem.EntityPropertyValue;
import org.termx.ts.property.PropertyReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.Pair;

import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class ConceptExportService {
  private final ConceptService conceptService;
  private final CodeSystemService codeSystemService;
  private final LorqueProcessService lorqueProcessService;

  private final static String process = "cs-concept-export";

  public LorqueProcess export(String codeSystem, String version, String format) {
    LorqueProcess lorqueProcess = lorqueProcessService.start(new LorqueProcess().setProcessName(process));
    CompletableFuture.runAsync(SessionStore.wrap(() -> {
      try {
        ProcessResult result = ProcessResult.binary(composeResult(codeSystem, version, format));
        lorqueProcessService.complete(lorqueProcess.getId(), result);
      } catch (Exception e) {
        ProcessResult result = ProcessResult.text(ExceptionUtils.getMessage(e) + "\n" + ExceptionUtils.getStackTrace(e));
        lorqueProcessService.fail(lorqueProcess.getId(), result);
      }
    }), VirtualThreadExecutor.get());

    return lorqueProcess;
  }

  private byte[] composeResult(String codeSystemId, String version, String format) {
    log.debug("=== EXPORT DEBUG: composeResult START ===");
    log.debug("EXPORT DEBUG: Exporting CodeSystem: {}, Version: {}, Format: {}", codeSystemId, version, format);
    
    CodeSystem codeSystem = codeSystemService.load(codeSystemId).orElseThrow();
    log.debug("EXPORT DEBUG: CodeSystem loaded - ID: {}, Name: {}, Title: {}", 
        codeSystem.getId(), codeSystem.getName(), codeSystem.getTitle());
    
    List<Concept> concepts = conceptService.query(new ConceptQueryParams().setCodeSystem(codeSystemId).setCodeSystemVersion(version).all()).getData();
    log.debug("EXPORT DEBUG: Loaded {} concepts from database", concepts.size());

    List<Pair<String, String>> associations = concepts.stream().flatMap(c -> c.getVersions().stream())
        .flatMap(v -> Optional.ofNullable(v.getAssociations()).orElse(List.of()).stream()
            .filter(a -> PublicationStatus.active.equals(a.getStatus()))
            .map(a -> Pair.of(v.getCode(), a.getTargetCode()))).toList();
    Map<String, List<String>> children = associations.stream().collect(Collectors.groupingBy(Pair::getValue, mapping(Pair::getKey, toList())));
    Map<String, List<String>> parents = associations.stream().collect(Collectors.groupingBy(Pair::getKey, mapping(Pair::getValue, toList())));

    log.debug("EXPORT DEBUG: About to compose headers...");
    List<String> headers = composeHeaders(codeSystem, concepts);
    log.debug("EXPORT DEBUG: Headers composed, about to validate designations...");
    validateDesignationsPresent(codeSystem, concepts, headers);
    log.debug("EXPORT DEBUG: Validation passed, composing rows...");
    List<Object[]> rows = concepts.stream().map(c -> composeRow(c, headers, children, parents)).toList();
    log.debug("EXPORT DEBUG: Composed {} rows", rows.size());

    if ("csv".equals(format)) {
      byte[] result = CsvUtil.composeCsv(headers, rows, ",").toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
      log.debug("EXPORT DEBUG: CSV export completed, size: {} bytes", result.length);
      log.debug("=== EXPORT DEBUG: composeResult END ===");
      return result;
    }
    if ("xlsx".equals(format)) {
      byte[] result = XlsxUtil.composeXlsx(headers, rows, "concepts");
      log.debug("EXPORT DEBUG: XLSX export completed, size: {} bytes", result.length);
      log.debug("=== EXPORT DEBUG: composeResult END ===");
      return result;
    }
    throw ApiError.TE807.toApiException();
  }

  private List<String> composeHeaders(CodeSystem codeSystem, List<Concept> concepts) {
    log.debug("=== EXPORT DEBUG: composeHeaders START ===");
    log.debug("EXPORT DEBUG: CodeSystem ID: {}, Concepts count: {}", codeSystem.getId(), concepts.size());
    
    List<String> fields = new ArrayList<>();
    fields.add("code");
    
    // Collect designations to determine max counts per type#language combination
    Map<String, Integer> designationMaxCounts = new java.util.HashMap<>();
    
    // Collect property values to determine max counts per property
    Map<String, Integer> propertyMaxCounts = new java.util.HashMap<>();
    Map<String, String> propertyTypes = new java.util.HashMap<>();
    
    int conceptLimit = Math.min(concepts.size(), 1000);
    for (int i = 0; i < conceptLimit; i++) {
      Concept c = concepts.get(i);
      if (c == null) {
        continue;
      }
      List<CodeSystemEntityVersion> versions = Optional.ofNullable(c.getVersions()).orElse(List.of());
      for (CodeSystemEntityVersion v : versions) {
        if (v == null) {
          continue;
        }
        // Group designations by type#language to count them
        List<Designation> designationsList = Optional.ofNullable(v.getDesignations()).orElse(List.of());
        log.debug("EXPORT DEBUG: composeHeaders - Concept: {}, Version: {}, Total designations: {}", 
            c.getCode(), v.getId(), designationsList.size());
        
        for (Designation d : designationsList) {
          log.debug("EXPORT DEBUG: composeHeaders - Examining designation - Concept: {}, Version: {}, Status: '{}', Type: '{}', Language: '{}'", 
              c.getCode(), v.getId(), 
              d != null ? d.getStatus() : "null",
              d != null ? d.getDesignationType() : "null",
              d != null ? d.getLanguage() : "null");
        }
        
        Map<String, List<Designation>> designationGroups = designationsList.stream()
            .filter(d -> {
              boolean isNotNull = d != null;
              String status = d != null ? d.getStatus() : null;
              boolean isActiveOrDraft = isNotNull && (PublicationStatus.active.equals(status) || "draft".equals(status));
              log.debug("EXPORT DEBUG: composeHeaders - Filtering designation - Concept: {}, NotNull: {}, IsActiveOrDraft: {}, Status: '{}'", 
                  c.getCode(), isNotNull, isActiveOrDraft, status);
              return isNotNull && isActiveOrDraft;
            })
            .filter(d -> {
              // Only include designations with a valid type (not null, not empty, not just whitespace)
              String type = d.getDesignationType();
              boolean hasValidType = type != null && !type.trim().isEmpty();
              log.debug("EXPORT DEBUG: composeHeaders - Type check - Concept: {}, HasValidType: {}, Type: '{}'", 
                  c.getCode(), hasValidType, type);
              return hasValidType;
            })
            .collect(Collectors.groupingBy(d -> {
              String type = d.getDesignationType() != null ? d.getDesignationType().trim() : "";
              String lang = d.getLanguage() != null ? d.getLanguage().trim() : "";
              return type + "#" + lang;
            }));
        
        log.debug("EXPORT DEBUG: composeHeaders - Concept: {}, Version: {}, Designation groups after filtering: {}", 
            c.getCode(), v.getId(), designationGroups.size());
        
        designationGroups.forEach((typeLang, designations) -> {
          if (designations != null && !designations.isEmpty()) {
            int count = designations.size();
            designationMaxCounts.merge(typeLang, count, Integer::max);
            log.debug("EXPORT DEBUG: Found designation group - Concept: {}, Version: {}, TypeLang: {}, Count: {}", 
                c.getCode(), v.getId(), typeLang, count);
          }
        });
        
        // Group property values by property name to count them
        Map<String, List<EntityPropertyValue>> propertyGroups = Optional.ofNullable(v.getPropertyValues()).orElse(List.of()).stream()
            .filter(pv -> pv.getEntityProperty() != null && !pv.getEntityProperty().isEmpty())
            .collect(Collectors.groupingBy(EntityPropertyValue::getEntityProperty));
        
        propertyGroups.forEach((propertyName, values) -> {
          if (values != null && !values.isEmpty() && values.get(0) != null && values.get(0).getEntityPropertyType() != null) {
            propertyTypes.put(propertyName, values.get(0).getEntityPropertyType());
            int count = values.size();
            propertyMaxCounts.merge(propertyName, count, Integer::max);
          }
        });
      }
    }
    
    // Generate designation column names with order format (optional when maxCount == 1)
    // Separate display designations from other designations
    java.util.List<String> displayDesignationColumns = new java.util.ArrayList<>();
    java.util.List<String> otherDesignationColumns = new java.util.ArrayList<>();
    log.debug("EXPORT DEBUG: designationMaxCounts size: {}", designationMaxCounts.size());
    designationMaxCounts.forEach((typeLang, maxCount) -> {
      log.debug("EXPORT DEBUG: Processing designation typeLang: {}, maxCount: {}", typeLang, maxCount);
      boolean isDisplay = typeLang.startsWith("display#");
      for (int order = 1; order <= maxCount; order++) {
        // Omit ##1 suffix when there's only one designation of this type#language
        String columnName;
        if (maxCount == 1) {
          columnName = typeLang;
        } else {
          columnName = typeLang + "##" + order;
        }
        if (isDisplay) {
          displayDesignationColumns.add(columnName);
        } else {
          otherDesignationColumns.add(columnName);
        }
        log.debug("EXPORT DEBUG: Added designation column: {} (isDisplay: {})", columnName, isDisplay);
      }
    });
    // Sort display designations alphabetically
    displayDesignationColumns.sort(String::compareTo);
    // Sort other designations alphabetically
    otherDesignationColumns.sort(String::compareTo);
    log.debug("EXPORT DEBUG: Total designation columns generated: {} (display: {}, other: {})", 
        displayDesignationColumns.size() + otherDesignationColumns.size(), 
        displayDesignationColumns.size(), otherDesignationColumns.size());
    log.debug("EXPORT DEBUG: Display designation columns: {}", displayDesignationColumns);
    log.debug("EXPORT DEBUG: Other designation columns: {}", otherDesignationColumns);
    // Add display designations first, then other designations
    fields.addAll(displayDesignationColumns);
    fields.addAll(otherDesignationColumns);
    
    // Generate column names based on property type and max counts
    // Use List instead of TreeSet to control ordering: group code/system by order
    // Omit ##1 suffix when maxCount == 1 for cleaner format
    // Build a map of property name to its columns for sorting by property order
    java.util.Map<String, java.util.List<String>> propertyColumnsMap = new java.util.HashMap<>();
    propertyMaxCounts.forEach((propertyName, maxCount) -> {
      java.util.List<String> columns = new java.util.ArrayList<>();
      String type = propertyTypes.get(propertyName);
      if (EntityPropertyType.coding.equals(type)) {
        // For coding properties: create code and system columns grouped by order
        // When maxCount == 1: propertyName#code, propertyName#system
        // When maxCount > 1: propertyName#code##1, propertyName#system##1, propertyName#code##2, propertyName#system##2, ...
        for (int order = 1; order <= maxCount; order++) {
          if (maxCount == 1) {
            columns.add(propertyName + "#code");
            columns.add(propertyName + "#system");
          } else {
            columns.add(propertyName + "#code##" + order);
            columns.add(propertyName + "#system##" + order);
          }
        }
      } else {
        // For simple properties: create columns with order
        // When maxCount == 1: propertyName
        // When maxCount > 1: propertyName##1, propertyName##2, ...
        for (int order = 1; order <= maxCount; order++) {
          if (maxCount == 1) {
            columns.add(propertyName);
          } else {
            columns.add(propertyName + "##" + order);
          }
        }
      }
      propertyColumnsMap.put(propertyName, columns);
    });
    
    // Get property order from CodeSystem properties definition
    // Properties are ordered by their position in the CodeSystem properties list
    java.util.Map<String, Integer> propertyOrderMap = new java.util.HashMap<>();
    List<EntityProperty> codeSystemProperties = Optional.ofNullable(codeSystem.getProperties()).orElse(List.of());
    for (int i = 0; i < codeSystemProperties.size(); i++) {
      EntityProperty prop = codeSystemProperties.get(i);
      if (prop != null && prop.getName() != null) {
        propertyOrderMap.put(prop.getName(), i);
      }
    }
    
    // Sort properties by their order in CodeSystem definition, then alphabetically for properties not in definition
    java.util.List<String> propertyColumns = new java.util.ArrayList<>();
    propertyColumnsMap.entrySet().stream()
        .sorted((e1, e2) -> {
          String prop1 = e1.getKey();
          String prop2 = e2.getKey();
          Integer order1 = propertyOrderMap.getOrDefault(prop1, Integer.MAX_VALUE);
          Integer order2 = propertyOrderMap.getOrDefault(prop2, Integer.MAX_VALUE);
          if (!order1.equals(order2)) {
            return Integer.compare(order1, order2);
          }
          // If same order (or both not in definition), sort alphabetically
          return prop1.compareTo(prop2);
        })
        .forEach(entry -> {
          java.util.List<String> columns = entry.getValue();
          // Sort columns within each property to maintain code/system grouping
          columns.sort((a, b) -> {
            // Extract order numbers
            int orderA = extractOrder(a);
            int orderB = extractOrder(b);
            if (orderA != orderB) {
              return Integer.compare(orderA, orderB);
            }
            // Same order: code comes before system
            boolean isCodeA = a.contains("#code") && !a.contains("#system");
            boolean isCodeB = b.contains("#code") && !b.contains("#system");
            if (isCodeA && !isCodeB) return -1;
            if (!isCodeA && isCodeB) return 1;
            return 0;
          });
          propertyColumns.addAll(columns);
        });
    
    fields.addAll(propertyColumns);
    fields.addAll(Optional.ofNullable(codeSystem.getProperties()).orElse(List.of()).stream()
        .map(EntityProperty::getName)
        .filter(p -> List.of("status", "is-a", "parent", "child", "partOf", "groupedBy", "classifiedWith").contains(p)).toList());
    
    log.debug("EXPORT DEBUG: Total headers generated: {}", fields.size());
    log.debug("EXPORT DEBUG: All headers: {}", fields);
    log.debug("=== EXPORT DEBUG: composeHeaders END ===");
    return fields;
  }

  private Object[] composeRow(Concept c, List<String> headers, Map<String, List<String>> children, Map<String, List<String>> parents) {
    List<Object> row = new ArrayList<>();
    // Group designations by type#language, preserving order
    Map<String, List<Designation>> designationsByTypeLang = Optional.ofNullable(c.getVersions()).orElse(List.of()).stream()
        .filter(v -> v != null)
        .flatMap(v -> Optional.ofNullable(v.getDesignations()).orElse(List.of()).stream())
        .filter(d -> {
          if (d == null) return false;
          String status = d.getStatus();
          return PublicationStatus.active.equals(status) || "draft".equals(status);
        })
        .filter(d -> {
          // Only include designations with a valid type (not null, not empty, not just whitespace)
          String type = d.getDesignationType();
          return type != null && !type.trim().isEmpty();
        })
        .collect(Collectors.groupingBy(d -> {
          String type = d.getDesignationType() != null ? d.getDesignationType().trim() : "";
          String lang = d.getLanguage() != null ? d.getLanguage().trim() : "";
          return type + "#" + lang;
        }, Collectors.toList()));
    
    // Group property values by property name, preserving order
    Map<String, List<EntityPropertyValue>> propertiesByProperty = c.getVersions().stream()
        .flatMap(v -> Optional.ofNullable(v.getPropertyValues()).orElse(List.of()).stream())
        .filter(pv -> pv != null && pv.getEntityProperty() != null && !pv.getEntityProperty().isEmpty())
        .collect(Collectors.groupingBy(EntityPropertyValue::getEntityProperty, Collectors.toList()));
    
    log.debug("EXPORT DEBUG: composeRow - Concept: {}, Properties by property: {}", 
        c.getCode(), propertiesByProperty.keySet());
    propertiesByProperty.forEach((propName, values) -> {
      log.debug("EXPORT DEBUG: composeRow - Concept: {}, Property: {}, Count: {}, Types: {}", 
          c.getCode(), propName, values.size(), 
          values.stream().map(v -> v != null ? v.getEntityPropertyType() : "null").toList());
    });
    
    headers.forEach(h -> {
      log.debug("EXPORT DEBUG: composeRow - Concept: {}, Processing header: {}", c.getCode(), h);
      
      if ("code".equals(h)) {
        row.add(c.getCode());
      } else if ("status".equals(h)) {
        row.add(c.getVersions().stream().findFirst().map(CodeSystemEntityVersion::getStatus).orElse(""));
      } else if (List.of("is-a", "parent", "partOf", "groupedBy", "classifiedWith").contains(h)) {
        row.add(String.join("#", parents.getOrDefault(c.getCode(), List.of())));
      } else if ("child".equals(h)) {
        row.add(String.join("#", children.getOrDefault(c.getCode(), List.of())));
      } else {
        // First check if this is a property column (has #code or #system suffix)
        // This must be checked BEFORE designation columns to avoid false matches
        // (e.g., "type#code##1" would match designation pattern but is actually a property)
        boolean isPropertyColumn = h.contains("#code") || h.contains("#system");
        
        if (isPropertyColumn) {
          // Parse column header to extract property name, type, and order
          ColumnInfo columnInfo = parseColumnHeader(h);
          if (columnInfo != null && columnInfo.propertyName != null && !columnInfo.propertyName.isEmpty()) {
            log.debug("EXPORT DEBUG: composeRow - Concept: {}, Header: {}, Parsed: propertyName={}, order={}, isCoding={}, isSystem={}", 
                c.getCode(), h, columnInfo.propertyName, columnInfo.order, columnInfo.isCoding, columnInfo.isSystem);
            
            if (propertiesByProperty.containsKey(columnInfo.propertyName)) {
              List<EntityPropertyValue> propertyValues = propertiesByProperty.get(columnInfo.propertyName);
              log.debug("EXPORT DEBUG: composeRow - Concept: {}, Property: {}, Found {} values, Requested order: {}", 
                  c.getCode(), columnInfo.propertyName, propertyValues.size(), columnInfo.order);
              
              // Get the property value at the specified order (1-based, so subtract 1 for index)
              if (columnInfo.order > 0 && columnInfo.order <= propertyValues.size()) {
                EntityPropertyValue pv = propertyValues.get(columnInfo.order - 1);
                log.debug("EXPORT DEBUG: composeRow - Concept: {}, Property: {}, Order: {}, PropertyValue: {}, Type: {}", 
                    c.getCode(), columnInfo.propertyName, columnInfo.order, 
                    pv != null ? "not null" : "null",
                    pv != null ? pv.getEntityPropertyType() : "null");
                
                if (pv != null) {
                  if (columnInfo.isCoding) {
                    try {
                      EntityPropertyValue.EntityPropertyValueCodingValue codingValue = pv.asCodingValue();
                      log.debug("EXPORT DEBUG: composeRow - Concept: {}, Property: {}, CodingValue: {}", 
                          c.getCode(), columnInfo.propertyName, codingValue != null ? "not null" : "null");
                      
                      if (columnInfo.isSystem) {
                        String system = codingValue != null ? codingValue.getCodeSystem() : null;
                        log.debug("EXPORT DEBUG: composeRow - Concept: {}, Property: {}, System: {}", 
                            c.getCode(), columnInfo.propertyName, system);
                        row.add(system != null ? system : "");
                      } else {
                        String code = codingValue != null ? codingValue.getCode() : null;
                        log.debug("EXPORT DEBUG: composeRow - Concept: {}, Property: {}, Code: {}", 
                            c.getCode(), columnInfo.propertyName, code);
                        row.add(code != null ? code : "");
                      }
                    } catch (Exception e) {
                      log.warn("EXPORT DEBUG: composeRow - Concept: {}, Property: {}, Error parsing coding value: {}", 
                          c.getCode(), columnInfo.propertyName, e.getMessage(), e);
                      row.add(""); // Error parsing coding value
                    }
                  } else {
                    // Simple property type
                    try {
                      if (pv.getEntityPropertyType() != null) {
                        if (pv.getEntityPropertyType().equals(EntityPropertyType.dateTime)) {
                          row.add(pv.asDateTimeValue() != null ? pv.asDateTimeValue().toLocalDate().toString() : "");
                        } else if (pv.getEntityPropertyType().equals(EntityPropertyType.decimal)) {
                          // Use toPlainString() instead of toString() to avoid scientific notation (e.g., 1E+1 -> 10)
                          row.add(pv.asDecimal() != null ? pv.asDecimal().stripTrailingZeros().toPlainString() : "");
                        } else {
                          Object value = pv.getValue();
                          if (value instanceof String) {
                            row.add((String) value);
                          } else if (value != null) {
                            row.add(JsonUtil.toJson(value));
                          } else {
                            row.add("");
                          }
                        }
                      } else {
                        row.add("");
                      }
                    } catch (Exception e) {
                      row.add(""); // Error processing property value
                    }
                  }
                } else {
                  row.add(""); // Property value is null
                }
              } else {
                log.debug("EXPORT DEBUG: composeRow - Concept: {}, Property: {}, Order {} out of bounds (size: {})", 
                    c.getCode(), columnInfo.propertyName, columnInfo.order, propertyValues.size());
                row.add(""); // No value at this order position
              }
            } else {
              log.debug("EXPORT DEBUG: composeRow - Concept: {}, Property: {} not found in propertiesByProperty", 
                  c.getCode(), columnInfo.propertyName);
              row.add(""); // Property not found
            }
          } else {
            log.debug("EXPORT DEBUG: composeRow - Concept: {}, Header: {} could not be parsed or property name is null", 
                c.getCode(), h);
            row.add(""); // Could not parse column header or property name is null
          }
        } else {
          // Check if this is a designation column (format: type#language##order)
          DesignationColumnInfo designationInfo = parseDesignationColumn(h);
          log.debug("EXPORT DEBUG: composeRow - Concept: {}, Header: {}, DesignationInfo: {}", 
              c.getCode(), h, designationInfo != null ? ("type=" + designationInfo.type + ", lang=" + designationInfo.language + ", order=" + designationInfo.order) : "null");
          
          if (designationInfo != null && designationInfo.type != null && !designationInfo.type.isEmpty()) {
            String typeLang = designationInfo.type + "#" + (designationInfo.language != null ? designationInfo.language : "");
            if (designationsByTypeLang.containsKey(typeLang)) {
              List<Designation> designations = designationsByTypeLang.get(typeLang);
              if (designationInfo.order > 0 && designationInfo.order <= designations.size()) {
                Designation designation = designations.get(designationInfo.order - 1);
                row.add(designation != null && designation.getName() != null ? designation.getName() : "");
              } else {
                row.add(""); // No designation at this order position
              }
            } else {
              row.add("");
            }
          } else {
            // Not a property column and not a designation column - try parsing as simple property
            ColumnInfo columnInfo = parseColumnHeader(h);
            if (columnInfo != null && columnInfo.propertyName != null && !columnInfo.propertyName.isEmpty()) {
              log.debug("EXPORT DEBUG: composeRow - Concept: {}, Header: {}, Parsed as simple property: propertyName={}, order={}, isCoding={}, isSystem={}", 
                  c.getCode(), h, columnInfo.propertyName, columnInfo.order, columnInfo.isCoding, columnInfo.isSystem);
              
              if (propertiesByProperty.containsKey(columnInfo.propertyName)) {
                List<EntityPropertyValue> propertyValues = propertiesByProperty.get(columnInfo.propertyName);
                log.debug("EXPORT DEBUG: composeRow - Concept: {}, Property: {}, Found {} values, Requested order: {}", 
                    c.getCode(), columnInfo.propertyName, propertyValues.size(), columnInfo.order);
                
                // Get the property value at the specified order (1-based, so subtract 1 for index)
                if (columnInfo.order > 0 && columnInfo.order <= propertyValues.size()) {
                  EntityPropertyValue pv = propertyValues.get(columnInfo.order - 1);
                  log.debug("EXPORT DEBUG: composeRow - Concept: {}, Property: {}, Order: {}, PropertyValue: {}, Type: {}", 
                      c.getCode(), columnInfo.propertyName, columnInfo.order, 
                      pv != null ? "not null" : "null",
                      pv != null ? pv.getEntityPropertyType() : "null");
                  
                  if (pv != null) {
                    // Simple property type
                    try {
                      if (pv.getEntityPropertyType() != null) {
                        if (pv.getEntityPropertyType().equals(EntityPropertyType.dateTime)) {
                          row.add(pv.asDateTimeValue() != null ? pv.asDateTimeValue().toLocalDate().toString() : "");
                        } else if (pv.getEntityPropertyType().equals(EntityPropertyType.decimal)) {
                          // Use toPlainString() instead of toString() to avoid scientific notation (e.g., 1E+1 -> 10)
                          row.add(pv.asDecimal() != null ? pv.asDecimal().stripTrailingZeros().toPlainString() : "");
                        } else {
                          Object value = pv.getValue();
                          if (value instanceof String) {
                            row.add((String) value);
                          } else if (value != null) {
                            row.add(JsonUtil.toJson(value));
                          } else {
                            row.add("");
                          }
                        }
                      } else {
                        row.add("");
                      }
                    } catch (Exception e) {
                      row.add(""); // Error processing property value
                    }
                  } else {
                    row.add(""); // Property value is null
                  }
                } else {
                  log.debug("EXPORT DEBUG: composeRow - Concept: {}, Property: {}, Order {} out of bounds (size: {})", 
                      c.getCode(), columnInfo.propertyName, columnInfo.order, propertyValues.size());
                  row.add(""); // No value at this order position
                }
              } else {
                log.debug("EXPORT DEBUG: composeRow - Concept: {}, Property: {} not found in propertiesByProperty", 
                    c.getCode(), columnInfo.propertyName);
                row.add(""); // Property not found
              }
            } else {
              log.debug("EXPORT DEBUG: composeRow - Concept: {}, Header: {} could not be parsed as property or designation", 
                  c.getCode(), h);
              row.add(""); // Could not parse column header
            }
          }
        }
      }
    });
    return row.toArray();
  }
  
  private ColumnInfo parseColumnHeader(String header) {
    // Pattern: {property-name}##{order} for simple types (or {property-name} when order is 1)
    // Pattern: {property-name}#code##{order} or {property-name}#system##{order} for coding types
    // Pattern: {property-name}#code or {property-name}#system when order is 1
    log.debug("EXPORT DEBUG: parseColumnHeader - Parsing header: {}", header);
    
    if (header == null) {
      log.debug("EXPORT DEBUG: parseColumnHeader - Header is null");
      return null;
    }
    
    boolean hasOrderSuffix = header.contains("##");
    int order = 1; // Default order when suffix is omitted
    String prefix = header;
    
    if (hasOrderSuffix) {
      String[] parts = header.split("##", 2);
      if (parts.length != 2) {
        log.debug("EXPORT DEBUG: parseColumnHeader - Header '{}' has ## but split failed", header);
        return null;
      }
      try {
        order = Integer.parseInt(parts[1]);
        prefix = parts[0];
        log.debug("EXPORT DEBUG: parseColumnHeader - Header '{}' split: prefix='{}', order={}", header, prefix, order);
      } catch (NumberFormatException e) {
        log.debug("EXPORT DEBUG: parseColumnHeader - Header '{}' order parse failed: {}", header, e.getMessage());
        return null;
      }
    }
    
    if (prefix == null || prefix.isEmpty()) {
      log.debug("EXPORT DEBUG: parseColumnHeader - Header '{}' prefix is null or empty", header);
      return null;
    }
    
    boolean isCoding = false;
    boolean isSystem = false;
    String propertyName;
    
    if (prefix.endsWith("#code")) {
      isCoding = true;
      isSystem = false;
      propertyName = prefix.substring(0, prefix.length() - 5); // Remove "#code"
      log.debug("EXPORT DEBUG: parseColumnHeader - Header '{}' is coding code, propertyName='{}'", header, propertyName);
    } else if (prefix.endsWith("#system")) {
      isCoding = true;
      isSystem = true;
      propertyName = prefix.substring(0, prefix.length() - 7); // Remove "#system"
      log.debug("EXPORT DEBUG: parseColumnHeader - Header '{}' is coding system, propertyName='{}'", header, propertyName);
    } else {
      isCoding = false;
      propertyName = prefix;
      log.debug("EXPORT DEBUG: parseColumnHeader - Header '{}' is simple property, propertyName='{}'", header, propertyName);
    }
    
    if (propertyName == null || propertyName.isEmpty()) {
      log.debug("EXPORT DEBUG: parseColumnHeader - Header '{}' propertyName is null or empty", header);
      return null;
    }
    
    ColumnInfo result = new ColumnInfo(propertyName, order, isCoding, isSystem);
    log.debug("EXPORT DEBUG: parseColumnHeader - Header '{}' parsed successfully: propertyName='{}', order={}, isCoding={}, isSystem={}", 
        header, propertyName, order, isCoding, isSystem);
    return result;
  }
  
  private static String extractPropertyName(String columnName) {
    // Remove #code, #system, and ##order suffixes to get the base property name
    // Handle both formats: with ## suffix and without (for single values)
    String beforeOrder = columnName;
    if (columnName.contains("##")) {
      beforeOrder = columnName.split("##")[0];
    }
    if (beforeOrder.endsWith("#code")) {
      return beforeOrder.substring(0, beforeOrder.length() - 5);
    } else if (beforeOrder.endsWith("#system")) {
      return beforeOrder.substring(0, beforeOrder.length() - 7);
    }
    return beforeOrder;
  }
  
  private static int extractOrder(String columnName) {
    // Extract order from column name, defaulting to 1 when no ## suffix is present
    if (columnName != null && columnName.contains("##")) {
      try {
        String[] parts = columnName.split("##", 2);
        return Integer.parseInt(parts[1]);
      } catch (NumberFormatException e) {
        return 1; // Default to order 1 on parse error
      }
    }
    return 1; // Default to order 1 when no suffix is present
  }
  
  private DesignationColumnInfo parseDesignationColumn(String header) {
    // Pattern: {type}#{language}##{order} or {type}#{language} when order is 1
    if (header == null) {
      return null;
    }
    
    boolean hasOrderSuffix = header.contains("##");
    int order = 1; // Default order when suffix is omitted
    String typeLang = header;
    
    if (hasOrderSuffix) {
      String[] parts = header.split("##", 2);
      if (parts.length != 2) {
        return null;
      }
      try {
        order = Integer.parseInt(parts[1]);
        typeLang = parts[0];
      } catch (NumberFormatException e) {
        return null;
      }
    }
    
    if (typeLang != null && typeLang.contains("#")) {
      String[] typeLangParts = typeLang.split("#", 2);
      if (typeLangParts.length == 2) {
        String type = typeLangParts[0] != null ? typeLangParts[0] : "";
        String lang = typeLangParts[1] != null ? typeLangParts[1] : "";
        return new DesignationColumnInfo(type, lang, order);
      }
    }
    return null;
  }
  
  private static class ColumnInfo {
    final String propertyName;
    final int order;
    final boolean isCoding;
    final boolean isSystem;
    
    ColumnInfo(String propertyName, int order, boolean isCoding, boolean isSystem) {
      this.propertyName = propertyName;
      this.order = order;
      this.isCoding = isCoding;
      this.isSystem = isSystem;
    }
  }
  
  private static class DesignationColumnInfo {
    final String type;
    final String language;
    final int order;
    
    DesignationColumnInfo(String type, String language, int order) {
      this.type = type;
      this.language = language;
      this.order = order;
    }
  }
  
  /**
   * Validates that all designations from the database are present in the export headers.
   * Throws an exception with diagnostic information if any designation is missing.
   */
  private void validateDesignationsPresent(CodeSystem codeSystem, List<Concept> concepts, List<String> headers) {
    log.debug("=== EXPORT DEBUG: validateDesignationsPresent START ===");
    log.debug("EXPORT DEBUG: CodeSystem ID: {}, Concepts count: {}, Headers count: {}", 
        codeSystem.getId(), concepts.size(), headers.size());
    
    // Collect all designations from all concepts
    Map<String, List<String>> designationsByConcept = new java.util.HashMap<>();
    
    int totalDesignationsFound = 0;
    for (Concept c : concepts) {
      if (c == null || c.getCode() == null) {
        log.debug("EXPORT DEBUG: Skipping null concept or concept without code");
        continue;
      }
      List<CodeSystemEntityVersion> versions = Optional.ofNullable(c.getVersions()).orElse(List.of());
      log.debug("EXPORT DEBUG: Concept '{}' has {} versions", c.getCode(), versions.size());
      
      for (CodeSystemEntityVersion v : versions) {
        if (v == null) {
          log.debug("EXPORT DEBUG: Skipping null version for concept '{}'", c.getCode());
          continue;
        }
        List<Designation> designationsList = Optional.ofNullable(v.getDesignations()).orElse(List.of());
        log.debug("EXPORT DEBUG: Concept '{}', Version '{}' has {} designations", 
            c.getCode(), v.getId(), designationsList.size());
        
        for (Designation d : designationsList) {
          log.debug("EXPORT DEBUG: Examining designation - Concept: {}, Version: {}, Designation: {}, Status: '{}', Type: '{}', Language: '{}'", 
              c.getCode(), v.getId(), 
              d != null ? ("ID=" + d.getId() + ", Name=" + d.getName()) : "null",
              d != null ? d.getStatus() : "null",
              d != null ? d.getDesignationType() : "null",
              d != null ? d.getLanguage() : "null");
          
          if (d == null) {
            log.warn("EXPORT DEBUG: Designation is null - Concept: {}, Version: {}", c.getCode(), v.getId());
            continue;
          }
          
          String status = d.getStatus();
          boolean isActiveOrDraft = PublicationStatus.active.equals(status) || "draft".equals(status);
          log.debug("EXPORT DEBUG: Designation status check - Concept: {}, Status: '{}', ActiveOrDraft: {}", 
              c.getCode(), status, isActiveOrDraft);
          
          if (isActiveOrDraft) {
            String type = d.getDesignationType();
            log.debug("EXPORT DEBUG: Processing designation - Concept: {}, Version: {}, Type: '{}', Language: '{}', Status: {}", 
                c.getCode(), v.getId(), type, d.getLanguage(), d.getStatus());
            
            if (type != null && !type.trim().isEmpty()) {
              String lang = d.getLanguage() != null ? d.getLanguage().trim() : "";
              String typeLang = type.trim() + "#" + lang;
              designationsByConcept.computeIfAbsent(c.getCode(), k -> new java.util.ArrayList<>()).add(typeLang);
              totalDesignationsFound++;
              log.debug("EXPORT DEBUG: Added designation to collection - Concept: {}, TypeLang: '{}'", c.getCode(), typeLang);
            } else {
              log.warn("EXPORT DEBUG: Skipping designation with null/empty type - Concept: {}, Language: '{}', Type value: '{}'", 
                  c.getCode(), d.getLanguage(), type);
            }
          } else {
            log.warn("EXPORT DEBUG: Skipping designation - Concept: {}, Status: '{}' (expected: '{}' or 'draft')", 
                c.getCode(), status, PublicationStatus.active);
          }
        }
      }
    }
    
    log.debug("EXPORT DEBUG: Total designations collected: {}", totalDesignationsFound);
    log.debug("EXPORT DEBUG: Designations by concept: {}", designationsByConcept);
    log.debug("EXPORT DEBUG: Headers to check against: {}", headers);
    
    // Count total designations (active and non-active) for diagnostic purposes
    int totalDesignationsInDb = 0;
    int activeDesignationsInDb = 0;
    int draftDesignationsInDb = 0;
    for (Concept c : concepts) {
      if (c == null || c.getCode() == null) continue;
      for (CodeSystemEntityVersion v : Optional.ofNullable(c.getVersions()).orElse(List.of())) {
        if (v == null) continue;
        List<Designation> allDesignations = Optional.ofNullable(v.getDesignations()).orElse(List.of());
        totalDesignationsInDb += allDesignations.size();
        for (Designation d : allDesignations) {
          if (d != null) {
            if (PublicationStatus.active.equals(d.getStatus())) {
              activeDesignationsInDb++;
            } else if ("draft".equals(d.getStatus())) {
              draftDesignationsInDb++;
            }
          }
        }
      }
    }
    log.debug("EXPORT DEBUG: Designation statistics - Total in DB: {}, Active: {}, Draft: {}, Collected for export: {}", 
        totalDesignationsInDb, activeDesignationsInDb, draftDesignationsInDb, totalDesignationsFound);
    
    // Check if all designations have corresponding columns in headers
    List<String> missingDesignations = new java.util.ArrayList<>();
    for (Map.Entry<String, List<String>> entry : designationsByConcept.entrySet()) {
      String conceptCode = entry.getKey();
      for (String typeLang : entry.getValue()) {
        // Check if this typeLang has a corresponding column in headers
        // It could be without suffix (single value) or with ##1, ##2, etc.
        boolean found = false;
        String matchingHeader = null;
        for (String header : headers) {
          if (header.equals(typeLang) || header.startsWith(typeLang + "##")) {
            found = true;
            matchingHeader = header;
            break;
          }
        }
        if (!found) {
          missingDesignations.add(conceptCode + ":" + typeLang);
          log.warn("EXPORT DEBUG: MISSING designation - Concept: {}, TypeLang: '{}' (no matching header found)", 
              conceptCode, typeLang);
        } else {
          log.debug("EXPORT DEBUG: FOUND designation - Concept: {}, TypeLang: '{}' matches header: '{}'", 
              conceptCode, typeLang, matchingHeader);
        }
      }
    }
    
    // Check if there are designation columns in headers
    boolean hasDesignationColumns = headers.stream()
        .anyMatch(h -> {
          DesignationColumnInfo info = parseDesignationColumn(h);
          return info != null && info.type != null && !info.type.isEmpty();
        });
    
    log.debug("EXPORT DEBUG: Has designation columns in headers: {}", hasDesignationColumns);
    
    // If any active designation is missing, throw exception with diagnostic information
    if (!missingDesignations.isEmpty()) {
      log.error("EXPORT DEBUG: VALIDATION FAILED - {} missing active designations found: {}", 
          missingDesignations.size(), missingDesignations);
      
      // Get CodeSystem description (title, name, or fallback to ID)
      String description = codeSystem.getTitle() != null && codeSystem.getTitle().containsKey("en") 
          ? codeSystem.getTitle().get("en")
          : (codeSystem.getName() != null ? codeSystem.getName() : codeSystem.getId());
      
      // Build diagnostic information about CodeSystem structure
      int totalConcepts = concepts.size();
      int conceptsWithDesignations = (int) concepts.stream()
          .filter(c -> c != null && c.getVersions() != null)
          .filter(c -> c.getVersions().stream()
              .anyMatch(v -> v != null && v.getDesignations() != null && !v.getDesignations().isEmpty()))
          .count();
      
      String missingDesignationsStr = String.join(", ", missingDesignations);
      String diagnosticInfo = String.format(
          "CodeSystem Structure - ID: %s, Description: %s, Total concepts: %d, Concepts with designations: %d, Total designations in DB: %d (Active: %d, Draft: %d), Active designations collected: %d, Missing designations: %s",
          codeSystem.getId(),
          description,
          totalConcepts,
          conceptsWithDesignations,
          totalDesignationsInDb,
          activeDesignationsInDb,
          draftDesignationsInDb,
          totalDesignationsFound,
          missingDesignationsStr
      );
      
      log.error("EXPORT DEBUG: Throwing TE808 exception with diagnostic info: {}", diagnosticInfo);
      throw ApiError.TE808.toApiException(Map.of(
          "codeSystemId", codeSystem.getId(),
          "description", description,
          "missingDesignations", missingDesignationsStr,
          "diagnosticInfo", diagnosticInfo
      ));
    } 
    
    // If there are active or draft designations in DB but no designation columns in export, throw exception
    int activeOrDraftDesignationsInDb = activeDesignationsInDb + draftDesignationsInDb;
    if (activeOrDraftDesignationsInDb > 0 && !hasDesignationColumns) {
      log.error("EXPORT DEBUG: VALIDATION FAILED - {} active or draft designations exist in database but no designation columns in export (Active: {}, Draft: {})", 
          activeOrDraftDesignationsInDb, activeDesignationsInDb, draftDesignationsInDb);
      
      // Get CodeSystem description (title, name, or fallback to ID)
      String description = codeSystem.getTitle() != null && codeSystem.getTitle().containsKey("en") 
          ? codeSystem.getTitle().get("en")
          : (codeSystem.getName() != null ? codeSystem.getName() : codeSystem.getId());
      
      // Build diagnostic information about CodeSystem structure
      int totalConcepts = concepts.size();
      int conceptsWithDesignations = (int) concepts.stream()
          .filter(c -> c != null && c.getVersions() != null)
          .filter(c -> c.getVersions().stream()
              .anyMatch(v -> v != null && v.getDesignations() != null && !v.getDesignations().isEmpty()))
          .count();
      
      String diagnosticInfo = String.format(
          "CodeSystem Structure - ID: %s, Description: %s, Total concepts: %d, Concepts with designations: %d, Total designations in DB: %d (Active: %d, Draft: %d), Active designations collected: %d, No designation columns found in export headers",
          codeSystem.getId(),
          description,
          totalConcepts,
          conceptsWithDesignations,
          totalDesignationsInDb,
          activeDesignationsInDb,
          draftDesignationsInDb,
          totalDesignationsFound
      );
      
      String missingDesignationsStr = String.format("No designation columns in export (Active or Draft designations: %d, Active: %d, Draft: %d)", 
          activeOrDraftDesignationsInDb, activeDesignationsInDb, draftDesignationsInDb);
      
      log.error("EXPORT DEBUG: Throwing TE808 exception - designations exist but not exported: {}", diagnosticInfo);
      throw ApiError.TE808.toApiException(Map.of(
          "codeSystemId", codeSystem.getId(),
          "description", description,
          "missingDesignations", missingDesignationsStr,
          "diagnosticInfo", diagnosticInfo
      ));
    } else {
      log.debug("EXPORT DEBUG: VALIDATION PASSED - All active and draft designations have corresponding headers");
    }
    log.debug("=== EXPORT DEBUG: validateDesignationsPresent END ===");
  }
}
