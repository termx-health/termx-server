package org.termx.core.sys.server;

import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SqlBuilder;
import org.termx.core.util.canonical.AuthoritativeUrlMatcher;
import org.termx.core.util.canonical.CanonicalUrlParser;
import org.termx.sys.server.TerminologyServer.AuthoritativeResource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import jakarta.inject.Singleton;

@Singleton
public class TerminologyServerAuthoritativeService extends BaseRepository {

  private static final Map<String, String[]> RESOURCE_TYPE_CONFIG = Map.of(
      "code-systems", new String[]{
          "select cs.id, cs.uri, csv.version, csv.status from terminology.code_system cs " +
              "left join terminology.code_system_version csv on csv.code_system = cs.id and csv.sys_status = 'A' " +
              "where cs.sys_status = 'A'",
          "CodeSystem"},
      "value-sets", new String[]{
          "select vs.id, vs.uri, vsv.version, vsv.status from terminology.value_set vs " +
              "left join terminology.value_set_version vsv on vsv.value_set = vs.id and vsv.sys_status = 'A' " +
              "where vs.sys_status = 'A'",
          "ValueSet"},
      "concept-maps", new String[]{
          "select ms.id, ms.uri, msv.version, msv.status from terminology.map_set ms " +
              "left join terminology.map_set_version msv on msv.map_set = ms.id and msv.sys_status = 'A' " +
              "where ms.sys_status = 'A'",
          "ConceptMap"},
      "structure-definitions", new String[]{
          "select sd.id::text as id, sd.url as uri, sdv.version, sdv.status " +
              "from modeler.structure_definition sd " +
              "left join modeler.structure_definition_version sdv on sdv.structure_definition_id = sd.id and sdv.sys_status = 'A' " +
              "where sd.sys_status = 'A'",
          "StructureDefinition"},
      "structure-maps", new String[]{
          "select td.id::text as id, td.url as uri, null as version, null as status " +
              "from modeler.transformation_definition td where td.sys_status = 'A'",
          "StructureMap"}
  );

  public List<AuthoritativeResource> findMatchingResources(String resourceType, List<AuthoritativeResource> patterns) {
    String[] config = RESOURCE_TYPE_CONFIG.get(resourceType);
    if (config == null || config[0] == null || patterns == null || patterns.isEmpty()) {
      return new ArrayList<>();
    }

    String query = config[0];
    String fhirType = config[1];

    SqlBuilder sb = new SqlBuilder(query);
    List<Map<String, Object>> rows = jdbcTemplate.queryForList(sb.getSql(), sb.getParams());

    List<AuthoritativeResource> result = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      String uri = (String) row.get("uri");
      String version = row.get("version") != null ? row.get("version").toString() : null;
      String status = (String) row.get("status");

      for (AuthoritativeResource pattern : patterns) {
        if (matchesPattern(pattern, uri, version, status, fhirType)) {
          AuthoritativeResource match = new AuthoritativeResource();
          match.setUrl(uri);
          match.setVersion(version);
          match.setStatus(status);
          match.setName((String) row.get("id"));
          result.add(match);
          break;
        }
      }
    }
    return result;
  }

  private boolean matchesPattern(AuthoritativeResource pattern, String uri, String version, String status, String fhirType) {
    String configuredUrl = pattern.toEcosystemUrl();
    CanonicalUrlParser parsed = CanonicalUrlParser.parse(configuredUrl);

    if (!AuthoritativeUrlMatcher.matches(parsed.getBaseUrl(), uri, fhirType)) {
      return false;
    }
    if (parsed.getVersion() != null && version != null) {
      if (!parsed.getVersion().equals(version)) {
        return false;
      }
    }
    if (parsed.getStatus() != null && status != null) {
      if (!parsed.getStatus().equals(status)) {
        return false;
      }
    }
    return true;
  }
}
