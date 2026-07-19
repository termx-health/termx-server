package org.termx.wiki.migration;

import liquibase.change.custom.CustomTaskChange;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.CustomChangeException;
import liquibase.exception.SetupException;
import liquibase.exception.ValidationErrors;
import liquibase.resource.ResourceAccessor;
import org.termx.wiki.WikiContentNormalizer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * One-time migration that converges existing wiki page content to mdbook-compatible syntax
 * (see {@link WikiContentNormalizer} and {@code docs/wiki-mdbook-syntax.md}). For every active
 * page content whose body would change, the verbatim original is preserved in
 * {@code bak_content} and {@code content} is replaced with the normalized form. Rows that need
 * no change are left untouched (including {@code bak_content}), so the migration is idempotent.
 */
public class WikiContentConvergenceChange implements CustomTaskChange {
  private String confirmationMessage = "wiki.page_content converged to mdbook-compatible syntax";

  @Override
  public void execute(Database database) throws CustomChangeException {
    Connection conn = ((JdbcConnection) database.getConnection()).getUnderlyingConnection();
    int rewritten = 0;
    try (Statement select = conn.createStatement();
         ResultSet rs = select.executeQuery(
             "select id, content from wiki.page_content where sys_status = 'A' and content is not null");
         PreparedStatement update = conn.prepareStatement(
             "update wiki.page_content set bak_content = ?, content = ? where id = ?")) {
      while (rs.next()) {
        long id = rs.getLong("id");
        String content = rs.getString("content");
        String normalized = WikiContentNormalizer.normalize(content);
        if (!normalized.equals(content)) {
          update.setString(1, content);    // bak_content = verbatim original
          update.setString(2, normalized); // content = converged form
          update.setLong(3, id);
          update.addBatch();
          rewritten++;
        }
      }
      update.executeBatch();
      confirmationMessage = "wiki.page_content convergence: " + rewritten
          + " row(s) rewritten (originals saved to bak_content)";
    } catch (Exception e) {
      throw new CustomChangeException("Wiki content convergence failed", e);
    }
  }

  @Override
  public String getConfirmationMessage() {
    return confirmationMessage;
  }

  @Override
  public void setUp() throws SetupException {
    // no setup required
  }

  @Override
  public void setFileOpener(ResourceAccessor resourceAccessor) {
    // no resources required
  }

  @Override
  public ValidationErrors validate(Database database) {
    return new ValidationErrors();
  }
}
