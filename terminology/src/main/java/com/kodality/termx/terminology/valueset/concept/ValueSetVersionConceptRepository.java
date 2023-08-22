package com.kodality.termx.terminology.valueset.concept;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.commons.util.JsonUtil;
import com.kodality.termx.ts.codesystem.Designation;
import com.kodality.termx.ts.valueset.ValueSetVersionConcept;
import java.util.List;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class ValueSetVersionConceptRepository extends BaseRepository {

  private final PgBeanProcessor bp = new PgBeanProcessor(ValueSetVersionConcept.class, bp -> {
    bp.addColumnProcessor("concept", PgBeanProcessor.fromJson());
    bp.addColumnProcessor("display", PgBeanProcessor.fromJson());
    bp.addColumnProcessor("additional_designations", PgBeanProcessor.fromJson(JsonUtil.getListType(Designation.class)));
  });

  public List<ValueSetVersionConcept> expand(Long valueSetVersionId) {
    long start = System.currentTimeMillis();
    SqlBuilder sb = new SqlBuilder("select * from terminology.value_set_expand(?::bigint)", valueSetVersionId);
    log.info("Value set expand function took " + (System.currentTimeMillis() - start) / 1000 + " seconds");
    return getBeans(sb.getSql(), bp, sb.getParams());
  }
}
