package org.termx.snomed.integration.rf2.scan;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import jakarta.inject.Singleton;
import org.termx.snomed.rf2.SnomedRF2Upload;

@Singleton
public class SnomedRF2UploadCacheRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(SnomedRF2Upload.class);

  public Long save(SnomedRF2Upload upload) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", upload.getId());
    ssb.property("branch_path", upload.getBranchPath());
    ssb.property("rf2_type", upload.getRf2Type());
    ssb.property("create_code_system_version", upload.isCreateCodeSystemVersion());
    ssb.property("filename", upload.getFilename());
    ssb.property("zip_size", upload.getZipSize());
    ssb.property("zip_data", upload.getZipData());
    ssb.property("scan_lorque_id", upload.getScanLorqueId());
    ssb.property("imported", upload.isImported());
    ssb.property("started", upload.getStarted());
    ssb.property("imported_at", upload.getImportedAt());

    SqlBuilder sb = ssb.buildSave("sys.snomed_rf2_upload", "id");
    return jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
  }

  public SnomedRF2Upload load(Long id) {
    String sql = "select * from sys.snomed_rf2_upload where id = ? and sys_status = 'A'";
    return getBean(sql, bp, id);
  }

  public void markImported(Long id) {
    String sql = "update sys.snomed_rf2_upload set imported = true, imported_at = current_timestamp where id = ?";
    jdbcTemplate.update(sql, id);
  }

  public void cleanup(int daysOld) {
    String sql = "delete from sys.snomed_rf2_upload where started < current_timestamp - (? || ' days')::interval";
    jdbcTemplate.update(sql, String.valueOf(daysOld));
  }
}
