package org.termx.core.msdevops;

import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.http.client.annotation.Client;

import java.util.List;
import java.util.Map;

/**
 * http client to handle Azure devops API
 */
@Client("https://dev.azure.com")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
interface AzureDevOpsClient {

    // Getting all repositories in a project
    @Get("/{organization}/{project}/_apis/git/repositories?api-version=7.1-preview.1")
    Map<String, Object> listRepositories (
            @Header("Authorization") String authorization,
            @PathVariable String organization,
            @PathVariable String project
    );

    // Getting items (tree of files/folders) in a repo at a specific path and branch
    @Get("/{organization}/{project}/_apis/git/repositories/{repository}/items?scopePath={scopePath}&recursionLevel=Full&versionDescriptor.version={branch}&versionDescriptor.versionType=branch&includeContentMetadata=true&api-version=7.1-preview.1")
    AzureItemsResponse getRepositoryItems (
            @Header("Authorization") String authorization,
            @PathVariable String organization,
            @PathVariable String project,
            @PathVariable String repository,
            @QueryValue("scopePath") String scopePath,
            @QueryValue("branch") String branch
    );

    // Get info about a specific branch
    @Get("/{organization}/{project}/_apis/git/repositories/{repository}/refs?filter=heads/{branch}&api-version=7.1-preview.1")
    String getBranch (
            @Header("Authorization") String authorization,
            @PathVariable String organization,
            @PathVariable String project,
            @PathVariable String repository,
            @PathVariable String branch
    );

    @Get("/{organization}/{project}/_apis/git/repositories/{repository}/items?path={path}&includeContentMetadata=true&includeContent=true&versionDescriptor.version={branch}&versionDescriptor.versionType=branch&api-version=7.1-preview.1")
    AzureItemDetailResponse getFileContent (
            @Header("Authorization") String authorization,
            @PathVariable String organization,
            @PathVariable String project,
            @PathVariable String repository,
            @QueryValue("path") String path,
            @QueryValue("branch") String branch
    );

    // Getting details of a single repository
    @Get("/{organization}/{project}/_apis/git/repositories/{repository}?api-version=7.0")
    Map<String, Object> getRepository(
            @Header("Authorization") String authorization,
            @PathVariable String organization,
            @PathVariable String project,
            @PathVariable String repository
    );

    // Getting all branches in a repository
    @Get("/{organization}/{project}/_apis/git/repositories/{repositoryId}/refs?filter=heads/&api-version=7.0")
    Map<String, Object> listBranches(
            @Header("Authorization") String authorization,
            @PathVariable String organization,
            @PathVariable String project,
            @PathVariable String repositoryId
    );

    // Getting commits in the repository
    @Get("/{organization}/{project}/_apis/git/repositories/{repositoryId}/commits?api-version=7.0")
    Map<String, Object> listCommits(
            @Header("Authorization") String authorization,
            @PathVariable String organization,
            @PathVariable String project,
            @PathVariable String repositoryId
    );

    // Getting Pull Requests in the Repository
    @Get("/{organization}/{project}/_apis/git/repositories/{repositoryId}/pullrequests?api-version=7.0")
    Map<String, Object> listPullRequests(
            @Header("Authorization") String authorization,
            @PathVariable String organization,
            @PathVariable String project,
            @PathVariable String repositoryId
    );

    @Post("/{organization}/{project}/_apis/git/repositories/{repository}/refs?api-version=7.0")
    void createBranch(
            @Header("Authorization") String authorization,
            @PathVariable String organization,
            @PathVariable String project,
            @PathVariable String repository,
            @Body List<Map<String, String>> body
    );

    // Create a new Pull Request
    /*
     * {
     *   "sourceRefName": "refs/heads/feature-branch",
     *   "targetRefName": "refs/heads/main",
     *   "title": "My Pull Request",
     *   "description": "Please review this change.",
     *   "reviewers": []
     * }
     */
    @Post("/{organization}/{project}/_apis/git/repositories/{repositoryId}/pullrequests?api-version=7.0")
    Map<String, Object> createPullRequest(
            @Header("Authorization") String authorization,
            @PathVariable String organization,
            @PathVariable String project,
            @PathVariable String repositoryId,
            @Body Map<String, Object> prBody
    );

    @Post("/{organization}/{project}/_apis/git/repositories/{repository}/pushes?api-version=7.0")
    void pushCommit(
            @Header("Authorization") String authorization,
            @PathVariable String organization,
            @PathVariable String project,
            @PathVariable String repository,
            @Body Map<String, Object> body
    );

}
