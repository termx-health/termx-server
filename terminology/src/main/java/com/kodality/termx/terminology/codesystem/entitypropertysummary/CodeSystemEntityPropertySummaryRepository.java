package com.kodality.termx.terminology.codesystem.entitypropertysummary;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.terminology.codesystem.entitypropertysummary.CodeSystemEntityPropertyConceptSummary.CodeSystemEntityPropertyConceptSummaryItem;
import com.kodality.termx.terminology.codesystem.entitypropertysummary.CodeSystemEntityPropertySummary.CodeSystemEntityPropertySummaryItem;
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

  public List<CodeSystemEntityPropertyConceptSummaryItem> getConceptSummary(String codeSystem, String version, Long entityPropertyId) {
    SqlBuilder sb = new SqlBuilder("select * from terminology.entity_property_concept_summary(?::text, ?::text, ?::bigint)", codeSystem, version, entityPropertyId);
    return getBeans(sb.getSql(), cbp, sb.getParams());
  }
}
