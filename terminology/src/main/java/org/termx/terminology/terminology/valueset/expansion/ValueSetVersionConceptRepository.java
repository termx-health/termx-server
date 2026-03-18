package org.termx.terminology.terminology.valueset.expansion;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SqlBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.kodality.commons.util.JsonUtil;
import org.termx.ts.codesystem.Designation;
import org.termx.ts.valueset.ValueSetVersionConcept;
import java.util.List;
import java.util.Objects;
import java.util.stream.StreamSupport;
import jakarta.inject.Singleton;

@Singleton
public class ValueSetVersionConceptRepository extends BaseRepository {

  private final PgBeanProcessor bp = new PgBeanProcessor(ValueSetVersionConcept.class, bp -> {
    bp.addColumnProcessor("concept", PgBeanProcessor.fromJson());
    bp.addColumnProcessor("display", (rs, index, propType) -> parseDesignation(rs.getString(index)));
    bp.addColumnProcessor("additional_designations", (rs, index, propType) -> parseDesignations(rs.getString(index)));
  });

  public List<ValueSetVersionConcept> expand(Long valueSetVersionId) {
    SqlBuilder sb = new SqlBuilder("select * from terminology.value_set_expand(?::bigint)", valueSetVersionId);
    return getBeans(sb.getSql(), bp, sb.getParams());
  }

  public List<ValueSetVersionConcept> expandFromJson(String valueSetJson) {
    SqlBuilder sb = new SqlBuilder("select * from terminology.value_set_expand(?::text)", valueSetJson);
    return getBeans(sb.getSql(), bp, sb.getParams());
  }

  private static Designation parseDesignation(String json) {
    if (json == null) {
      return null;
    }
    try {
      JsonNode node = JsonUtil.getObjectMapper().readTree(json);
      return parseItem(node);
    } catch (Exception e) {
      throw parseException("Failed to parse designation json", e);
    }
  }

  private static List<Designation> parseDesignations(String json) {
    if (json == null) {
      return null;
    }
    try {
      JsonNode node = JsonUtil.getObjectMapper().readTree(json);
      if (node == null || node.isNull()) {
        return null;
      }
      if (node.isArray()) {
        return StreamSupport.stream(node.spliterator(), false)
            .map(ValueSetVersionConceptRepository::parseItem)
            .filter(Objects::nonNull)
            .toList();
      }
      Designation designation = parseItem(node);
      return designation == null ? null : List.of(designation);
    } catch (Exception e) {
      throw parseException("Failed to parse designation list json", e);
    }
  }

  private static Designation parseItem(JsonNode node) {
    try {
      if (node == null || node.isNull()) {
        return null;
      }
      if (node.isTextual()) {
        return new Designation().setName(node.asText());
      }
      return JsonUtil.getObjectMapper().treeToValue(node, Designation.class);
    } catch (Exception e) {
      throw parseException("Failed to parse designation item json", e);
    }
  }

  private static RuntimeException parseException(String message, Exception e) {
    return new RuntimeException(message, e instanceof RuntimeException re && re.getCause() instanceof Exception cause ? cause : e);
  }
}
