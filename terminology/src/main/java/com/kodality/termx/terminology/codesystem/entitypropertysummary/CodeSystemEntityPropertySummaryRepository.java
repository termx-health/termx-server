package com.kodality.termx.terminology.codesystem.entitypropertysummary;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.terminology.codesystem.entitypropertysummary.CodeSystemEntityPropertyConceptSummary.CodeSystemEntityPropertyConceptSummaryItem;
import com.kodality.termx.terminology.codesystem.entitypropertysummary.CodeSystemEntityPropertySummary.CodeSystemEntityPropertySummaryItem;
import io.micronaut.core.util.StringUtils;
import java.util.List;
import javax.inject.Singleton;

@Singleton
public class CodeSystemEntityPropertySummaryRepository extends BaseRepository {

  private final PgBeanProcessor bp = new PgBeanProcessor(CodeSystemEntityPropertySummaryItem.class, bp -> {
    bp.addColumnProcessor("prop_list", PgBeanProcessor.fromJson(JsonUtil.getListType(Object.class)));
  });

  private final PgBeanProcessor cbp = new PgBeanProcessor(CodeSystemEntityPropertyConceptSummaryItem.class, bp -> {
    bp.addColumnProcessor("concept_ids", PgBeanProcessor.fromJson(JsonUtil.getListType(Long.class)));
  });

  public List<CodeSystemEntityPropertySummaryItem> getSummary(String codeSystem, String version) {
    SqlBuilder sb = new SqlBuilder("select * from terminology.entity_property_summary(?::text, ?::text)", codeSystem, version);
    return getBeans(sb.getSql(), bp, sb.getParams());
  }

  public List<CodeSystemEntityPropertyConceptSummaryItem> getConceptSummary(String codeSystem, String version, Long entityPropertyId, String entityPropertyValues) {
    String sql = "select epv.value ->> 'code' property_code, count(*) concept_cnt, '[' || string_agg(cse.id::text, ',') || ']' concept_ids" +
        "  from terminology.code_system_version csv" +
        "       inner join terminology.code_system_entity cse ON csv.code_system = cse.code_system and cse.sys_status = 'A'" +
        "       inner join terminology.code_system_entity_version csev" +
        "               on csev.code_system = csv.code_system and csev.code_system_entity_id = cse.id and csev.sys_status = 'A'" +
        "       inner join terminology.entity_version_code_system_version_membership mem" +
        "               on csev.id = mem.code_system_entity_version_id and csv.id = mem.code_system_version_id and mem.sys_status = 'A'" +
        "       inner join terminology.entity_property_value epv ON csev.id =  epv.code_system_entity_version_id and epv.sys_status = 'A'" +
        "       inner join terminology.entity_property epd ON epd.id = epv.entity_property_id and epd.type = 'Coding' and epd.sys_status = 'A'" +
        " where csv.code_system = ?::text" +
        "   and (csv.version = ?::text or ?::text is null)" +
        "   and epd.id = ?::bigint" +
        "   and csv.sys_status = 'A'";
    SqlBuilder sb = new SqlBuilder(sql, codeSystem, version, version, entityPropertyId);
    if (StringUtils.isNotEmpty(entityPropertyValues)) {
      String[] values = entityPropertyValues.split(",");
      for (String v: values) {
        sb.append("and exists(select 1 from terminology.entity_property_value epv where csev.id =  epv.code_system_entity_version_id and epv.value ->> 'code' = ?::text)", v);
      }
    }
    sb.append("group by epv.value ->> 'code'");
    return getBeans(sb.getSql(), cbp, sb.getParams());
  }
}
