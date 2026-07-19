package org.termx.wiki.importer;

import io.micronaut.core.annotation.Introspected;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Request to import a wiki Space from a GitHub repository. Provide either {@code url}
 * (e.g. {@code https://github.com/owner/repo/tree/branch/dir}) or {@code owner} + {@code repo}.
 * {@code branch} defaults to the repo's default branch; {@code dir} is auto-detected from where
 * {@code pages.json} lives; {@code token} is only needed for private repositories.
 */
@Getter
@Setter
@Accessors(chain = true)
@Introspected
public class WikiGithubImportRequest {
  @NotNull
  private Long spaceId;
  private String url;
  private String owner;
  private String repo;
  private String branch;
  private String dir;
  private String token;
  private String lang; // language for imported content (GitBook has none); defaults to 'en'
}
