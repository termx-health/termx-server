package com.kodality.termserver.ts.codesystem.supplement;


import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.resultset.ResultSetUtil;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.termserver.codesystem.CodeSystemSupplement;
import com.kodality.termserver.codesystem.Designation;
import com.kodality.termserver.codesystem.EntityProperty;
import com.kodality.termserver.codesystem.EntityPropertyValue;
import java.util.List;
import javax.inject.Singleton;

@Singleton
public class CodeSystemSupplementRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(CodeSystemSupplement.class, bp -> {
    bp.addColumnProcessor("property_supplement", (rs, index, propType) -> new EntityProperty().setId(ResultSetUtil.getLong(rs, "property_supplement")));
    bp.addColumnProcessor("property_value_supplement", (rs, index, propType) -> new EntityPropertyValue().setId(ResultSetUtil.getLong(rs, "property_value_supplement")));
    bp.addColumnProcessor("designation_supplement", (rs, index, propType) -> new Designation().setId(ResultSetUtil.getLong(rs, "designation_supplement")));
  });

  public void save(CodeSystemSupplement supplement) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", supplement.getId());
    ssb.property("code_system", supplement.getCodeSystem());
    ssb.property("type", supplement.getType());
    ssb.property("description", supplement.getDescription());
    ssb.property("created", supplement.getCreated());
    ssb.property("property_supplement", supplement.getPropertySupplement() == null ? null : supplement.getPropertySupplement().getId());
    ssb.property("property_value_supplement", supplement.getPropertyValueSupplement() == null ? null : supplement.getPropertyValueSupplement().getId());
    ssb.property("designation_supplement", supplement.getDesignationSupplement() == null ? null : supplement.getDesignationSupplement().getId());

    SqlBuilder sb = ssb.buildSave("terminology.code_system_supplement", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    supplement.setId(id);
  }

  public List<CodeSystemSupplement> getSupplements(String codeSystem) {
    String sql = "select * from terminology.code_system_supplement where sys_status = 'A' and code_system = ?";
    return getBeans(sql, bp, codeSystem);
  }
}
