package org.termx.wiki.importer;

import io.micronaut.core.annotation.Introspected;

/** Summary of a completed GitHub import. */
@Introspected
public record WikiGithubImportResult(String repo, String branch, String dir, int pages, int attachments) {}
