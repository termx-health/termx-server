package com.kodality.termx.snomed.snomed.translation;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import com.kodality.termx.snomed.concept.SnomedTranslation;
import com.kodality.termx.snomed.concept.SnomedTranslationStatus;
import java.util.List;
import javax.inject.Singleton;

@Singleton
public class SnomedTranslationRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(SnomedTranslation.class);

  public void save(String conceptId, SnomedTranslation translation) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", translation.getId());
    ssb.property("description_id", translation.getDescriptionId());
    ssb.property("concept_id", conceptId);
    ssb.property("module", translation.getModule());
    ssb.property("language", translation.getLanguage());
    ssb.property("term", translation.getTerm());
    ssb.property("type", translation.getType());
    ssb.property("acceptability", translation.getAcceptability());
    ssb.property("status", translation.getStatus());
    SqlBuilder sb = ssb.buildSave("snomed.snomed_translation", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    translation.setId(id);
  }

  public List<Long> retain(String conceptId, List<SnomedTranslation> translations) {
    SqlBuilder sb = new SqlBuilder("select id from snomed.snomed_translation");
    sb.append("where concept_id = ? and sys_status = 'A'", conceptId);
    sb.andNotIn("id", translations, SnomedTranslation::getId);
    List<Long> ids = jdbcTemplate.queryForList(sb.getSql(), Long.class, sb.getParams());

    sb = new SqlBuilder("update snomed.snomed_translation set sys_status = 'C'");
    sb.append(" where concept_id = ? and sys_status = 'A'", conceptId);
    sb.andNotIn("id", translations, SnomedTranslation::getId);
    jdbcTemplate.update(sb.getSql(), sb.getParams());

    return ids;
  }

  public List<SnomedTranslation> load(String conceptId) {
    String sql = "select * from snomed.snomed_translation where sys_status = 'A' and concept_id = ?";
    return getBeans(sql, bp, conceptId);
  }

  public SnomedTranslation load(Long id) {
    String sql = "select * from snomed.snomed_translation where sys_status = 'A' and id = ?";
    return getBean(sql, bp, id);
  }

  public void saveDescriptionId(Long id, String descriptionId) {
    SqlBuilder sb = new SqlBuilder("update snomed.snomed_translation set description_id = ? where id = ?", descriptionId, id);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public void updateStatus(Long id, String status) {
    SqlBuilder sb = new SqlBuilder("update snomed.snomed_translation set status = ? where sys_status = 'A' and id = ?", status, id);
    jdbcTemplate.update(sb.getSql(), sb.getParams());
  }

  public List<SnomedTranslation> loadActive() {
    String sql = "select * from snomed.snomed_translation where sys_status = 'A' and status = ?";
    return getBeans(sql, bp, SnomedTranslationStatus.active);
  }
}
