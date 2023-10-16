package com.kodality.termx.terminology.terminology.codesystem.supplement;


import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.resultset.ResultSetUtil;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.termx.ts.codesystem.CodeSystemSupplement;
import com.kodality.termx.ts.codesystem.CodeSystemSupplementType;
import com.kodality.termx.ts.codesystem.Designation;
import com.kodality.termx.ts.codesystem.EntityProperty;
import com.kodality.termx.ts.codesystem.EntityPropertyValue;
import java.util.List;
import javax.inject.Singleton;

@Singleton
public class CodeSystemSupplementRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(CodeSystemSupplement.class, bp -> {
    bp.addColumnProcessor("target_id", "target", (rs, index, propType) -> {
      if (CodeSystemSupplementType.property.equals(rs.getString("target_type"))) {
        return new EntityProperty().setId(ResultSetUtil.getLong(rs, "target_id"));
      } else if (CodeSystemSupplementType.propertyValue.equals(rs.getString("target_type"))) {
        return new EntityPropertyValue().setId(ResultSetUtil.getLong(rs, "target_id"));
      }
      return new Designation().setId(ResultSetUtil.getLong(rs, "target_id"));
    });
  });

  public void save(CodeSystemSupplement supplement) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", supplement.getId());
    ssb.property("code_system", supplement.getCodeSystem());
    ssb.property("target_type", supplement.getTargetType());
    ssb.property("description", supplement.getDescription());
    ssb.property("created", supplement.getCreated());
    ssb.property("target_id",
        CodeSystemSupplementType.property.equals(supplement.getTargetType()) ? ((EntityProperty) supplement.getTarget()).getId() :
            CodeSystemSupplementType.propertyValue.equals(supplement.getTargetType()) ? ((EntityPropertyValue) supplement.getTarget()).getId() :
                ((Designation) supplement.getTarget()).getId());
    SqlBuilder sb = ssb.buildSave("terminology.code_system_supplement", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    supplement.setId(id);
  }

  public List<CodeSystemSupplement> getSupplements(String codeSystem) {
    String sql = "select * from terminology.code_system_supplement where sys_status = 'A' and code_system = ?";
    return getBeans(sql, bp, codeSystem);
  }

  public CodeSystemSupplement load(Long id) {
    String sql = "select * from terminology.code_system_supplement where sys_status = 'A' and id = ?";
    return getBean(sql, bp, id);
  }
}
