package org.termx.auth.externalclient;

import com.kodality.commons.db.bean.PgBeanProcessor;
import com.kodality.commons.db.repo.BaseRepository;
import jakarta.inject.Singleton;
import java.util.List;

@Singleton
public class ExternalClientRepository extends BaseRepository {
  private final PgBeanProcessor bp = new PgBeanProcessor(ExternalClient.class, p -> {
    p.addColumnProcessor("privileges", PgBeanProcessor.fromArray());
  });

  public List<ExternalClient> loadActive() {
    String sql = "select * from auth.external_client where active";
    return getBeans(sql, bp);
  }
}
