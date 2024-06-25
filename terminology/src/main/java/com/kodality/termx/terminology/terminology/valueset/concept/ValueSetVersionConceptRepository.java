package com.kodality.termx.terminology.terminology.valueset.concept;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.ts.codesystem.Designation;
import com.kodality.termx.ts.valueset.ValueSetVersionConcept;
import java.util.List;
import jakarta.inject.Singleton;

@Singleton
public class ValueSetVersionConceptRepository extends BaseRepository {

  private final PgBeanProcessor bp = new PgBeanProcessor(ValueSetVersionConcept.class, bp -> {
    bp.addColumnProcessor("concept", PgBeanProcessor.fromJson());
    bp.addColumnProcessor("display", PgBeanProcessor.fromJson());
    bp.addColumnProcessor("additional_designations", PgBeanProcessor.fromJson(JsonUtil.getListType(Designation.class)));
  });

  public List<ValueSetVersionConcept> expand(Long valueSetVersionId) {
    SqlBuilder sb = new SqlBuilder("select * from terminology.value_set_expand(?::bigint)", valueSetVersionId);
    return getBeans(sb.getSql(), bp, sb.getParams());
  }
}
