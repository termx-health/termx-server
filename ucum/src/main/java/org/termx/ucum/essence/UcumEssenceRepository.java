package org.termx.ucum.essence;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import jakarta.inject.Singleton;

@Singleton
public class UcumEssenceRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(UcumEssence.class);

  public UcumEssence loadActive() {
    String sql = "select id, version, essence_xml::text as xml from ucum.essence where sys_status = 'A' order by id desc limit 1";
    return getBean(sql, bp);
  }

  public void save(UcumEssence essence) {
    Long id = jdbcTemplate.queryForObject("""
        insert into ucum.essence(version, essence_xml)
        values (?, cast(? as xml))
        returning id
        """, Long.class, essence.getVersion(), essence.getXml());
    essence.setId(id);
  }

  public void cancelActive() {
    jdbcTemplate.update("update ucum.essence set sys_status = 'C' where sys_status = 'A'");
  }
}
