package org.termx.terminology.fileimporter.codesystem.utils;

import org.termx.terminology.ApiError;
import org.termx.terminology.fileimporter.codesystem.utils.CodeSystemFileImportRequest.FileProcessingProperty;
import org.termx.terminology.fileimporter.codesystem.utils.CodeSystemFileImportResult.FileProcessingEntityPropertyValue;
import org.termx.terminology.fileimporter.codesystem.utils.CodeSystemFileImportResult.FileProcessingResponseProperty;
import org.termx.terminology.fileimporter.fileparser.FileParserFactory;
import org.termx.terminology.fileimporter.fileparser.IFileParser;
import org.termx.ts.codesystem.EntityPropertyKind;
import org.termx.ts.codesystem.EntityPropertyType;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

import static org.termx.ts.codesystem.EntityPropertyType.bool;
import static org.termx.ts.codesystem.EntityPropertyType.coding;
import static org.termx.ts.codesystem.EntityPropertyType.dateTime;
import static org.termx.ts.codesystem.EntityPropertyType.decimal;
import static org.termx.ts.codesystem.EntityPropertyType.integer;

@Slf4j
public class CodeSystemFileImportProcessor {
  public static final String IDENTIFIER_PROPERTY = "concept-code";
  public static final String HIERARCHICAL_CONCEPT = "hierarchical-concept";
  public static final String DESIGNATION_PROPERTY_TYPE = "designation";
  public static final String RANDOM_UUID = UUID.randomUUID().toString();


  public static CodeSystemFileImportResult process(CodeSystemFileImportRequest request, byte[] file) {
    log.debug("=== IMPORT DEBUG: process START ===");
    log.debug("IMPORT DEBUG: File type: {}, File size: {} bytes", request.getType(), file != null ? file.length : 0);
    
    String type = request.getType();
    List<FileProcessingProperty> importProperties = request.getProperties();
    log.debug("IMPORT DEBUG: Total properties configured: {}", importProperties.size());
    importProperties.forEach(p -> log.debug("IMPORT DEBUG: Property - columnName: {}, propertyName: {}, propertyType: {}, language: {}", 
        p.getColumnName(), p.getName(), p.getPropertyType(), p.getLanguage()));

    log.debug("IMPORT DEBUG: Validating required properties...");
    if (importProperties.stream().noneMatch(p -> List.of(IDENTIFIER_PROPERTY, HIERARCHICAL_CONCEPT).contains(p.getName()))) {
      log.error("IMPORT DEBUG: No identifier property found (concept-code or hierarchical-concept)");
      throw ApiError.TE722.toApiException();
    }
    if (importProperties.stream().filter(p -> List.of(IDENTIFIER_PROPERTY, HIERARCHICAL_CONCEPT).contains(p.getName()) && p.isPreferred()).count() > 1) {
      log.error("IMPORT DEBUG: Multiple preferred identifier properties found");
      throw ApiError.TE707.toApiException();
    }
    if (importProperties.stream().noneMatch(p -> DESIGNATION_PROPERTY_TYPE.equals(p.getPropertyType()))) {
      log.error("IMPORT DEBUG: No designation property found");
      throw ApiError.TE721.toApiException();
    }
    if (importProperties.stream().anyMatch(p -> DESIGNATION_PROPERTY_TYPE.equals(p.getPropertyType()) && StringUtils.isEmpty(p.getLanguage()))) {
      log.error("IMPORT DEBUG: Designation property found without language");
      throw ApiError.TE728.toApiException();
    }
    importProperties.stream().filter(p -> p.getPropertyType() == null).findFirst().ifPresent(p -> {
      log.error("IMPORT DEBUG: Property type not specified for property: {}", p.getName());
      throw ApiError.TE706.toApiException(Map.of("propertyName", p.getName()));
    });
    log.debug("IMPORT DEBUG: Validation passed");

    List<FileProcessingProperty> identifierProperties = importProperties.stream()
        .filter(p -> IDENTIFIER_PROPERTY.equals(p.getName()))
        .sorted((o1, o2) -> Boolean.compare(o2.isPreferred(), o1.isPreferred()))
        .toList();
    log.debug("IMPORT DEBUG: Found {} identifier properties", identifierProperties.size());

    log.debug("IMPORT DEBUG: Parsing file...");
    IFileParser parser = FileParserFactory.getParser(type, file);
    List<String> headers = parser.getHeaders();
    List<String[]> rows = parser.getRows();
    log.debug("IMPORT DEBUG: File parsed - Headers: {}, Rows: {}", headers.size(), rows.size());
    log.debug("IMPORT DEBUG: Headers: {}", headers);

    log.debug("IMPORT DEBUG: Processing {} rows...", rows.size());
    var entities = rows.stream().map(r -> {
      int rowIndex = rows.indexOf(r);
      log.debug("IMPORT DEBUG: Processing row {} of {}", rowIndex + 1, rows.size());
      
      Map<String, List<FileProcessingEntityPropertyValue>> entity = new HashMap<>();
      // Track coding columns that need pairing (new format: property#code##order and property#system##order).
      // Outer key is the CSV column prefix (pairing key), not necessarily the mapped TermX property name.
      Map<String, Map<Integer, CodeSystemPair>> codingPairs = new HashMap<>(); // csvPrefix -> order -> (code, system)
      // Mapped entity property name per CSV prefix (from FileProcessingProperty); may differ from csvPrefix.
      Map<String, String> codingMappedPropertyNameByCsvPrefix = new HashMap<>();
      
      for (FileProcessingProperty prop : importProperties) {
        int idx = headers.indexOf(prop.getColumnName());
        if (idx == -1) {
          log.error("IMPORT DEBUG: Row {} - Column '{}' not found in file headers", rowIndex + 1, prop.getColumnName());
          throw ApiError.TE712.toApiException(Map.of("column", prop.getColumnName()));
        }
        if (idx >= r.length || r[idx] == null || StringUtils.isEmpty(r[idx])) {
          log.debug("IMPORT DEBUG: Row {} - Column '{}' (index {}) is empty, skipping", rowIndex + 1, prop.getColumnName(), idx);
          continue;
        }

        String cellValue = r[idx];
        log.debug("IMPORT DEBUG: Row {} - Processing column '{}' (index {}), value: '{}'", rowIndex + 1, prop.getColumnName(), idx, cellValue);

        // Check if this is a new format column
        ColumnParseResult parsed = parseNewFormatColumn(prop.getColumnName());
        if (parsed != null && parsed.isCoding) {
          // Pair by CSV prefix (parsed.propertyName); emit EntityPropertyValue under mapped name (prop.getName()).
          String csvPrefix = parsed.propertyName;
          String mappedPropertyName = prop.getName();
          int order = parsed.order;
          log.debug("IMPORT DEBUG: Row {} - Detected coding property column: csvPrefix={}, mappedPropertyName={}, order={}, isSystem={}",
              rowIndex + 1, csvPrefix, mappedPropertyName, order, parsed.isSystem);
          codingMappedPropertyNameByCsvPrefix.merge(csvPrefix, mappedPropertyName, (a, b) -> {
            if (!a.equals(b)) {
              log.warn("IMPORT DEBUG: Conflicting mapped property names for CSV prefix '{}': '{}' vs '{}', using '{}'",
                  csvPrefix, a, b, a);
            }
            return a;
          });
          codingPairs.computeIfAbsent(csvPrefix, k -> new HashMap<>());
          CodeSystemPair pair = codingPairs.get(csvPrefix).computeIfAbsent(order, k -> new CodeSystemPair());
          if (parsed.isSystem) {
            pair.system = cellValue;
            log.debug("IMPORT DEBUG: Row {} - Stored system value for csvPrefix '{}', order {}: '{}'",
                rowIndex + 1, csvPrefix, order, cellValue);
          } else {
            pair.code = cellValue;
            log.debug("IMPORT DEBUG: Row {} - Stored code value for csvPrefix '{}', order {}: '{}'",
                rowIndex + 1, csvPrefix, order, cellValue);
          }
        } else if (parsed != null && !parsed.isCoding && prop.getColumnName().contains("#")) {
          // This might be a designation column in new format (type#language##order or type#language)
          DesignationParseResult designationParsed = parseDesignationColumn(prop.getColumnName());
          if (designationParsed != null) {
            // This is a designation column in new format
            String propertyName = designationParsed.type;
            String language = designationParsed.language;
            log.debug("IMPORT DEBUG: Row {} - Detected designation column: type={}, language={}, order={}, value='{}'", 
                rowIndex + 1, propertyName, language, designationParsed.order, cellValue);
            // Create designation property value with language
            FileProcessingEntityPropertyValue ep = new FileProcessingEntityPropertyValue();
            ep.setColumnName(prop.getColumnName());
            ep.setPropertyName(propertyName);
            ep.setPropertyType(DESIGNATION_PROPERTY_TYPE);
            ep.setLang(language);
            ep.setValue(cellValue);
            var propertyValues = entity.getOrDefault(propertyName, new ArrayList<>());
            propertyValues.add(ep);
            entity.put(propertyName, propertyValues);
            log.debug("IMPORT DEBUG: Row {} - Created designation property value for '{}' (language: {})", 
                rowIndex + 1, propertyName, language);
          } else {
            // Process normally (old format or simple properties)
            log.debug("IMPORT DEBUG: Row {} - Processing as simple property: columnName={}, propertyName={}, propertyType={}", 
                rowIndex + 1, prop.getColumnName(), prop.getPropertyName(), prop.getPropertyType());
            List<FileProcessingEntityPropertyValue> values = mapPropValue(prop, cellValue);
            log.debug("IMPORT DEBUG: Row {} - Mapped {} values for property '{}'", rowIndex + 1, values.size(), prop.getPropertyName());
            var propertyValues = entity.getOrDefault(prop.getPropertyName(), new ArrayList<>());
            propertyValues.addAll(values);
            entity.put(prop.getPropertyName(), propertyValues);
          }
        } else {
          // Process normally (old format or simple properties)
          log.debug("IMPORT DEBUG: Row {} - Processing as simple property: columnName={}, propertyName={}, propertyType={}", 
              rowIndex + 1, prop.getColumnName(), prop.getPropertyName(), prop.getPropertyType());
          List<FileProcessingEntityPropertyValue> values = mapPropValue(prop, cellValue);
          log.debug("IMPORT DEBUG: Row {} - Mapped {} values for property '{}'", rowIndex + 1, values.size(), prop.getPropertyName());
          var propertyValues = entity.getOrDefault(prop.getPropertyName(), new ArrayList<>());
          propertyValues.addAll(values);
          entity.put(prop.getPropertyName(), propertyValues);
        }
      }
      
      // Process paired coding columns
      log.debug("IMPORT DEBUG: Row {} - Processing {} coding property pairs", rowIndex + 1, codingPairs.size());
      codingPairs.forEach((csvPrefix, orderMap) -> {
        String mappedPropertyName = codingMappedPropertyNameByCsvPrefix.getOrDefault(csvPrefix, csvPrefix);
        List<FileProcessingEntityPropertyValue> propertyValues = entity.computeIfAbsent(mappedPropertyName, k -> new ArrayList<>());
        orderMap.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
              CodeSystemPair pair = entry.getValue();
              if (pair.code != null && pair.system != null) {
                // Create coding value with both code and system
                log.debug("IMPORT DEBUG: Row {} - Creating coding value for mappedPropertyName '{}', csvPrefix '{}', order {}: code='{}', system='{}'",
                    rowIndex + 1, mappedPropertyName, csvPrefix, entry.getKey(), pair.code, pair.system);
                FileProcessingEntityPropertyValue ep = new FileProcessingEntityPropertyValue();
                ep.setColumnName(csvPrefix + "#code##" + entry.getKey() + "," + csvPrefix + "#system##" + entry.getKey());
                ep.setPropertyName(mappedPropertyName);
                ep.setPropertyType(coding);
                ep.setValue(Map.of("code", pair.code, "codeSystem", pair.system));
                propertyValues.add(ep);
              } else {
                log.warn("IMPORT DEBUG: Row {} - Incomplete coding pair for csvPrefix '{}', order {}: code={}, system={}",
                    rowIndex + 1, csvPrefix, entry.getKey(), pair.code != null, pair.system != null);
              }
            });
      });


      for (FileProcessingProperty prop : identifierProperties) {
        int idx = headers.indexOf(prop.getColumnName());
        if (r[idx] != null) {
          String identifierValue = r[idx];
          log.debug("IMPORT DEBUG: Row {} - Found identifier '{}' in column '{}'", rowIndex + 1, identifierValue, prop.getColumnName());
          List<FileProcessingEntityPropertyValue> ep = mapPropValue(prop, identifierValue);
          entity.put(prop.getPropertyName(), ep);
          break;
        }
      }

      if (request.isAutoConceptOrder()) {
        int order = (rowIndex + 1) * 10;
        log.debug("IMPORT DEBUG: Row {} - Auto concept order enabled, setting order: {}", rowIndex + 1, order);
        entity.put("conceptOrder", List.of(new FileProcessingEntityPropertyValue().setValue(order).setPropertyName("conceptOrder")));
      }
      
      String conceptCode = entity.getOrDefault(IDENTIFIER_PROPERTY, entity.get(HIERARCHICAL_CONCEPT)).stream()
          .findFirst().map(v -> (String) v.getValue()).orElse(null);
      log.debug("IMPORT DEBUG: Row {} - Entity created with concept code: '{}'", rowIndex + 1, conceptCode);
      return entity;
    }).filter(CollectionUtils::isNotEmpty)
        .collect(Collectors.groupingBy(r -> r.getOrDefault(IDENTIFIER_PROPERTY, r.get(HIERARCHICAL_CONCEPT)).stream()
            .findFirst().map(v -> (String) v.getValue()).orElse(RANDOM_UUID)));

    log.debug("IMPORT DEBUG: Grouped entities by concept code, total groups: {}", entities.size());
    
    if (entities.containsKey(RANDOM_UUID)) {
      log.error("IMPORT DEBUG: Found entities without concept code identifier");
      throw ApiError.TE722.toApiException();
    }
    if (entities.values().stream().anyMatch(v -> v.size() > 1)) {
      log.error("IMPORT DEBUG: Found duplicate concept codes");
      entities.entrySet().stream()
          .filter(e -> e.getValue().size() > 1)
          .forEach(e -> log.error("IMPORT DEBUG: Duplicate concept code '{}' found in {} rows", e.getKey(), e.getValue().size()));
      throw ApiError.TE738.toApiException();
    }

    log.debug("IMPORT DEBUG: Extracting unique properties from configuration...");
    List<FileProcessingResponseProperty> properties = importProperties.stream()
        .map(p -> {
          return new FileProcessingResponseProperty()
              .setPropertyName(p.getPropertyName() != null ? p.getPropertyName() : p.getColumnName())
              .setPropertyType(DESIGNATION_PROPERTY_TYPE.equals(p.getPropertyType()) ? EntityPropertyType.string : p.getPropertyType())
              .setPropertyKind(DESIGNATION_PROPERTY_TYPE.equals(p.getPropertyType()) ? EntityPropertyKind.designation : EntityPropertyKind.property)
              .setPropertyCodeSystem(p.getPropertyCodeSystem());
        })
        .filter(distinctByKey(FileProcessingResponseProperty::getPropertyName))
        .collect(Collectors.toCollection(ArrayList::new));
    log.debug("IMPORT DEBUG: Found {} unique properties", properties.size());
    
    if (request.isAutoConceptOrder()) {
      log.debug("IMPORT DEBUG: Adding conceptOrder property");
      properties.add(new FileProcessingResponseProperty().setPropertyName("conceptOrder").setPropertyType(integer).setPropertyKind(EntityPropertyKind.property));
    }

    int totalEntities = entities.values().stream().mapToInt(List::size).sum();
    log.debug("IMPORT DEBUG: Processing complete - Total entities: {}, Total properties: {}", totalEntities, properties.size());
    log.debug("=== IMPORT DEBUG: process END ===");
    
    return new CodeSystemFileImportResult().setEntities(entities.values().stream().flatMap(Collection::stream).toList()).setProperties(properties);
  }


  private static List<FileProcessingEntityPropertyValue> mapPropValue(FileProcessingProperty prop, String rawValue) {
    log.debug("IMPORT DEBUG: mapPropValue - columnName: {}, propertyName: {}, propertyType: {}, rawValue: '{}', delimiter: '{}'", 
        prop.getColumnName(), prop.getName(), prop.getPropertyType(), rawValue, prop.getPropertyDelimiter());
    
    List<String> rowValues = List.of(rawValue);
    if (StringUtils.isNotEmpty(prop.getPropertyDelimiter())) {
      rowValues = Arrays.stream(rawValue.split(Pattern.quote(prop.getPropertyDelimiter()))).map(String::trim).toList();
      log.debug("IMPORT DEBUG: mapPropValue - Split by delimiter '{}' into {} values: {}", 
          prop.getPropertyDelimiter(), rowValues.size(), rowValues);
    }

    return rowValues.stream().map(val -> {
      Object transformedValue = transformPropertyValue(val, prop.getPropertyType(), prop.getPropertyTypeFormat());
      log.debug("IMPORT DEBUG: mapPropValue - Transformed value '{}' (type: {}) to: {}", 
          val, prop.getPropertyType(), transformedValue);

      FileProcessingEntityPropertyValue ep = new FileProcessingEntityPropertyValue();
      ep.setColumnName(prop.getColumnName());
      ep.setPropertyName(prop.getName());
      ep.setPropertyType(DESIGNATION_PROPERTY_TYPE.equals(prop.getPropertyType()) ? EntityPropertyType.string : prop.getPropertyType());
      ep.setPropertyTypeFormat(prop.getPropertyTypeFormat());
      ep.setPropertyCodeSystem(prop.getPropertyCodeSystem());
      ep.setLang(prop.getLanguage());
      ep.setValue(transformedValue);
      return ep;
    }).toList();
  }


  private static Object transformPropertyValue(String val, String type, String dateFormat) {
    if (val == null || type == null) {
      log.debug("IMPORT DEBUG: transformPropertyValue - val or type is null, returning null");
      return null;
    }
    log.debug("IMPORT DEBUG: transformPropertyValue - Transforming value '{}' to type '{}' (format: '{}')", val, type, dateFormat);
    
    Object result = switch (type) {
      case bool -> {
        boolean boolValue = Stream.of("1", "true").anyMatch(v -> v.equalsIgnoreCase(val));
        log.debug("IMPORT DEBUG: transformPropertyValue - Boolean transformation: '{}' -> {}", val, boolValue);
        yield boolValue;
      }
      case integer -> {
        try {
          Integer intValue = Integer.valueOf(val);
          log.debug("IMPORT DEBUG: transformPropertyValue - Integer transformation: '{}' -> {}", val, intValue);
          yield intValue;
        } catch (NumberFormatException e) {
          log.warn("IMPORT DEBUG: transformPropertyValue - Failed to parse integer from '{}': {}", val, e.getMessage());
          throw e;
        }
      }
      case decimal -> {
        try {
          Double doubleValue = Double.valueOf(val);
          log.debug("IMPORT DEBUG: transformPropertyValue - Decimal transformation: '{}' -> {}", val, doubleValue);
          yield doubleValue;
        } catch (NumberFormatException e) {
          log.warn("IMPORT DEBUG: transformPropertyValue - Failed to parse decimal from '{}': {}", val, e.getMessage());
          throw e;
        }
      }
      case dateTime -> {
        Date dateValue = transformDate(val, dateFormat);
        log.debug("IMPORT DEBUG: transformPropertyValue - DateTime transformation: '{}' (format: '{}') -> {}", val, dateFormat, dateValue);
        yield dateValue;
      }
      case coding -> {
        Map<String, String> codingValue = Map.of("code", val);
        log.debug("IMPORT DEBUG: transformPropertyValue - Coding transformation: '{}' -> {}", val, codingValue);
        yield codingValue;
      }
      default -> {
        log.debug("IMPORT DEBUG: transformPropertyValue - String (default) transformation: '{}' -> '{}'", val, val);
        yield val;
      }
    };
    return result;
  }

  public static Date transformDate(String date, String format) {
    log.debug("IMPORT DEBUG: transformDate - Parsing date '{}' with format '{}'", date, format);
    SimpleDateFormat dateFormat = new SimpleDateFormat(format);
    dateFormat.setLenient(false);
    try {
      Date result = dateFormat.parse(date);
      log.debug("IMPORT DEBUG: transformDate - Successfully parsed date '{}' to: {}", date, result);
      return result;
    } catch (ParseException e) {
      log.warn("IMPORT DEBUG: transformDate - Failed to parse date '{}' with format '{}': {}", date, format, e.getMessage());
      return null;
    }
  }

  public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
    Set<Object> seen = ConcurrentHashMap.newKeySet();
    return t -> seen.add(keyExtractor.apply(t));
  }
  
  /**
   * Parses column names in the new format: {property}##{order} or {property}#code##{order} or {property}#system##{order}
   * Returns null if the column doesn't match the new format.
   */
  private static ColumnParseResult parseNewFormatColumn(String columnName) {
    log.debug("IMPORT DEBUG: parseNewFormatColumn - Parsing column name: '{}'", columnName);
    // Pattern: {property-name}##{order} for simple types (or {property-name} when order is 1)
    // Pattern: {property-name}#code##{order} or {property-name}#system##{order} for coding types
    // Pattern: {property-name}#code or {property-name}#system when order is 1
    if (columnName == null) {
      log.debug("IMPORT DEBUG: parseNewFormatColumn - Column name is null");
      return null;
    }
    
    boolean hasOrderSuffix = columnName.contains("##");
    int order = 1; // Default order when suffix is omitted
    String prefix = columnName;
    
    if (hasOrderSuffix) {
      String[] parts = columnName.split("##", 2);
      if (parts.length != 2) {
        log.debug("IMPORT DEBUG: parseNewFormatColumn - Failed to split order suffix from '{}'", columnName);
        return null;
      }
      try {
        order = Integer.parseInt(parts[1]);
        prefix = parts[0];
        log.debug("IMPORT DEBUG: parseNewFormatColumn - Extracted order {} from '{}', prefix: '{}'", order, columnName, prefix);
      } catch (NumberFormatException e) {
        log.debug("IMPORT DEBUG: parseNewFormatColumn - Failed to parse order from '{}': {}", columnName, e.getMessage());
        return null;
      }
    }
    
    boolean isCoding = false;
    boolean isSystem = false;
    String propertyName;
    
    if (prefix.endsWith("#code")) {
      isCoding = true;
      isSystem = false;
      propertyName = prefix.substring(0, prefix.length() - 5); // Remove "#code"
      log.debug("IMPORT DEBUG: parseNewFormatColumn - Detected coding code column: propertyName='{}', order={}", propertyName, order);
    } else if (prefix.endsWith("#system")) {
      isCoding = true;
      isSystem = true;
      propertyName = prefix.substring(0, prefix.length() - 7); // Remove "#system"
      log.debug("IMPORT DEBUG: parseNewFormatColumn - Detected coding system column: propertyName='{}', order={}", propertyName, order);
    } else {
      isCoding = false;
      propertyName = prefix;
      log.debug("IMPORT DEBUG: parseNewFormatColumn - Detected simple property column: propertyName='{}', order={}", propertyName, order);
    }
    
    ColumnParseResult result = new ColumnParseResult(propertyName, order, isCoding, isSystem);
    log.debug("IMPORT DEBUG: parseNewFormatColumn - Result: propertyName='{}', order={}, isCoding={}, isSystem={}", 
        result.propertyName, result.order, result.isCoding, result.isSystem);
    return result;
  }
  
  private static class ColumnParseResult {
    final String propertyName;
    final int order;
    final boolean isCoding;
    final boolean isSystem;
    
    ColumnParseResult(String propertyName, int order, boolean isCoding, boolean isSystem) {
      this.propertyName = propertyName;
      this.order = order;
      this.isCoding = isCoding;
      this.isSystem = isSystem;
    }
  }
  
  /**
   * Parses designation column names in the new format: {type}#{language}##{order} or {type}#{language} when order is 1
   * Returns null if the column doesn't match the designation format.
   */
  private static DesignationParseResult parseDesignationColumn(String columnName) {
    log.debug("IMPORT DEBUG: parseDesignationColumn - Parsing column name: '{}'", columnName);
    // Pattern: {type}#{language}##{order} or {type}#{language} when order is 1
    if (columnName == null) {
      log.debug("IMPORT DEBUG: parseDesignationColumn - Column name is null");
      return null;
    }
    
    boolean hasOrderSuffix = columnName.contains("##");
    int order = 1; // Default order when suffix is omitted
    String typeLang = columnName;
    
    if (hasOrderSuffix) {
      String[] parts = columnName.split("##", 2);
      if (parts.length != 2) {
        log.debug("IMPORT DEBUG: parseDesignationColumn - Failed to split order suffix from '{}'", columnName);
        return null;
      }
      try {
        order = Integer.parseInt(parts[1]);
        typeLang = parts[0];
        log.debug("IMPORT DEBUG: parseDesignationColumn - Extracted order {} from '{}', typeLang: '{}'", order, columnName, typeLang);
      } catch (NumberFormatException e) {
        log.debug("IMPORT DEBUG: parseDesignationColumn - Failed to parse order from '{}': {}", columnName, e.getMessage());
        return null;
      }
    }
    
    if (typeLang.contains("#")) {
      String[] typeLangParts = typeLang.split("#", 2);
      if (typeLangParts.length == 2) {
        DesignationParseResult result = new DesignationParseResult(typeLangParts[0], typeLangParts[1], order);
        log.debug("IMPORT DEBUG: parseDesignationColumn - Result: type='{}', language='{}', order={}", 
            result.type, result.language, result.order);
        return result;
      }
    }
    log.debug("IMPORT DEBUG: parseDesignationColumn - Column '{}' does not match designation format", columnName);
    return null;
  }
  
  private static class CodeSystemPair {
    String code;
    String system;
  }
  
  private static class DesignationParseResult {
    final String type;
    final String language;
    final int order;
    
    DesignationParseResult(String type, String language, int order) {
      this.type = type;
      this.language = language;
      this.order = order;
    }
  }
}
