package org.termx.core.acl;

import com.kodality.commons.db.repo.BaseRepository;
import jakarta.inject.Singleton;
import java.util.List;

@Singleton
public class AclRepository extends BaseRepository {

  public void permit(Long sId, String tenant, String access) {
    String sql = "insert into core.acl(s_id, tenant, access) select ?, ?, ? where not core.aclchk(?, ?, ?)";
    jdbcTemplate.update(sql, sId, tenant, access, sId, tenant, access);
  }

  public void init(Long sId, String tenant) {
    String sql = "insert into core.acl(s_id, tenant, access) select ?, ?, ? where not core.aclchk(?, ?, ?)";
    jdbcTemplate.update(sql, sId, tenant, "owner", sId, tenant, "edit");
  }

  public boolean check(Long sId, String tenant, String access) {
    String sql = "select core.aclchk(?, ?, ?)";
    return jdbcTemplate.queryForObject(sql, Boolean.class, sId, tenant, access);
  }

  public void revoke(Long sId, String tenant) {
    String sql = "update core.acl set sys_status = 'C' where s_id = ? and COALESCE(tenant, '--') = COALESCE(?, '--') and not core.aclchk(?, ?, ?)";
    jdbcTemplate.update(sql, sId, tenant, sId, tenant, "owner");
  }

  public void copy(Long fromSId, Long toSId) {
    String sql = "insert into core.acl(s_id, tenant, access) select ?, tenant, access from core.acl where s_id = ? and not core.aclchk(?, tenant, access)";
    jdbcTemplate.update(sql, toSId, fromSId, toSId);
  }

  public List<String> getTenants(Long sId, String access) {
    String sql = "select tenant from core.acl where s_id = ? and access = ?";
    return jdbcTemplate.queryForList(sql, String.class, sId, access);
  }
}
