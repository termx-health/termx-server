package com.kodality.commons.acl;

import com.kodality.commons.db.repo.BaseRepository;
import java.util.List;
import jakarta.inject.Singleton;


@Singleton
public class AclRepository extends BaseRepository {
  public void permit(Long sid, String tenant, String access) {
    String sql = "insert into core.acl(s_id, tenant, access) select ?, ?, ? where not core.aclchk(?, ?, ?)";
    jdbcTemplate.update(sql, sid, tenant, access, sid, tenant, access);
  }

  public void init(Long sid, String tenant) {
    String sql = "insert into core.acl(s_id, tenant, access) select ?, ?, ? where not core.aclchk(?, ?, ?)";
    jdbcTemplate.update(sql, sid, tenant, AclAccess.owner, sid, tenant, AclAccess.edit);
  }

  public boolean check(Long sid, String tenant, String access) {
    String sql = "select core.aclchk(?, ?, ?)";
    return jdbcTemplate.queryForObject(sql, Boolean.class, sid, tenant, access);
  }

  public void revoke(Long sid, String tenant) {
    String sql = "update core.acl set sys_status = 'C' where s_id = ? and COALESCE(tenant, '--') = COALESCE(?, '--') and not core.aclchk(?, ?, ?)";
    jdbcTemplate.update(sql, sid, tenant, sid, tenant, AclAccess.owner);
  }

  public void copy(Long fromSid, Long toSid) {
    String sql = "insert into core.acl(s_id, tenant, access)" +
        " select ?, tenant, access from core.acl where s_id = ? and not core.aclchk(?, tenant, access)";
    jdbcTemplate.update(sql, toSid, fromSid, toSid);
  }

  public List<String> getTenants(Long sid, String access) {
    String sql = "select tenant from core.acl where s_id = ? and access = ?";
    return jdbcTemplate.queryForList(sql, String.class, sid, access);
  }
}
