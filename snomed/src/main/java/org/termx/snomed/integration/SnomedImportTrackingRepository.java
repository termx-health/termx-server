package org.termx.snomed.integration;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import com.kodality.commons.db.sql.SaveSqlBuilder;
import com.kodality.commons.db.sql.SqlBuilder;
import org.termx.snomed.rf2.SnomedImportTracking;
import io.micronaut.core.util.CollectionUtils;
import jakarta.inject.Singleton;
import java.util.List;

@Singleton
public class SnomedImportTrackingRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(SnomedImportTracking.class);

  public Long save(SnomedImportTracking tracking) {
    SaveSqlBuilder ssb = new SaveSqlBuilder();
    ssb.property("id", tracking.getId());
    ssb.property("snowstorm_job_id", tracking.getSnowstormJobId());
    ssb.property("branch_path", tracking.getBranchPath());
    ssb.property("type", tracking.getType());
    ssb.property("status", tracking.getStatus());
    ssb.property("started", tracking.getStarted());
    ssb.property("finished", tracking.getFinished());
    ssb.property("error_message", tracking.getErrorMessage());
    ssb.property("notified", tracking.isNotified());

    SqlBuilder sb = ssb.buildSave("sys.snomed_import_tracking", "id");
    Long id = jdbcTemplate.queryForObject(sb.getSql(), Long.class, sb.getParams());
    return id;
  }

  public List<SnomedImportTracking> loadPending() {
    String sql = "SELECT * FROM sys.snomed_import_tracking WHERE status = 'RUNNING' AND (notified IS NULL OR notified = false) ORDER BY started";
    return getBeans(sql, bp);
  }

  public SnomedImportTracking load(Long id) {
    String sql = "SELECT * FROM sys.snomed_import_tracking WHERE id = ?";
    return getBean(sql, bp, id);
  }

  public void markNotified(Long id) {
    String sql = "UPDATE sys.snomed_import_tracking SET notified = true WHERE id = ?";
    jdbcTemplate.update(sql, id);
  }

  public void cleanup(int daysOld) {
    String sql = "DELETE FROM sys.snomed_import_tracking WHERE started < current_timestamp - interval '" + daysOld + " days'";
    jdbcTemplate.update(sql);
  }
}
