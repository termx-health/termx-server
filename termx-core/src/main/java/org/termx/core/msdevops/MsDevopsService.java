package org.termx.core.msdevops;

import com.google.common.collect.Sets;
import com.kodality.commons.client.HttpClientError;
import com.kodality.commons.util.JsonUtil;
import org.termx.core.github.GithubService;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

import static com.kodality.commons.stream.Collectors.toMap;

@Requires(property = "ms-devops.client.id")
@Singleton
@RequiredArgsConstructor
@Slf4j
public class MsDevopsService {
    private final AzureDevOpsClient azureDevOpsClient;

    @Value("${ms-devops.client.id}")
    private String clientId;
    @Value("${ms-devops.client.secret}")
    private String clientSecret;

    /**
     * Azure DevOps git over HTTPS is authenticated with a Personal Access Token (PAT) sent as HTTP Basic auth,
     * where the username is ignored and the PAT goes in the password position.
     * {@code ms-devops.client.id} is the (arbitrary) username and {@code ms-devops.client.secret} is the PAT.
     */
    public String getAuthorization() {
        return "Basic " + Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
    }

    /** Sync is PAT-based at the server level: it is available whenever a PAT is configured. */
    public boolean isAuthorized(String repo) {
        return StringUtils.isNotBlank(clientSecret);
    }

    public GithubService.GithubStatus status(String repo, String branch, String dir, List<GithubService.GithubContent> local) {
        // recursive tree, relative to 'dir'
        GithubService.GithubTree treeResp = getTree(repo, branch, dir);

        // absolute path -> tree
        Map<String, GithubService.GithubTreeItem> githubTree = getTreeAbsoluteBlobs(repo, branch, treeResp).stream().collect(toMap(GithubService.GithubTreeItem::getPath, c -> c));
        // absolute path -> content
        Map<String, GithubService.GithubContent> localContents = local.stream().collect(toMap(GithubService.GithubContent::getPath, c -> c));

        return calculateBlobStatus(repo, branch, githubTree, localContents);
    }

    public GithubService.GithubTree getTree(String repo, String branch, String path) {
        try {
            var resp = azureDevOpsClient.getRepositoryItems(getAuthorization(),
                    getOrg(repo), getProj(repo), getProj(repo), path, branch);
            return AzureToGithubMapper.map(resp, "");
        } catch (HttpClientError e) {
            if (e.getResponse().statusCode() == 404) {
                return null;
            }
            log.error("HttpClientError on getTree ", e);
            throw e;
        }
    }

    private List<GithubService.GithubTreeItem> getTreeAbsoluteBlobs(String repo, String branch, GithubService.GithubTree tree) {
        if (tree == null) {
            return List.of();
        }
        return tree.getTree().stream()
                .filter(t -> GithubService.GithubTreeItem.GithubTreeType.blob.equals(t.getType()))
                .peek(t ->
                        getContentDataFromMsDevops(repo, branch, t)
                )
                .toList();
    }

    private void getContentDataFromMsDevops(String repo, String branch, GithubService.GithubTreeItem t) {
        try {
            var resp = azureDevOpsClient.getFileContent(getAuthorization(),
                    getOrg(repo), getProj(repo), getProj(repo), t.getPath(), branch);
            resp.setSize(AzureToGithubMapper.extractSize(resp));
            t.setSize(resp.getSize());
            if ("base64".equalsIgnoreCase(resp.getContentMetadata().getEncoding())) {
                t.setSha(calculateSha(Base64.getDecoder().decode(resp.getContent())));
            } else {
                t.setSha(calculateSha(resp.getContent()));
            }
        } catch (HttpClientError e) {
            log.error("HttpClientError on getContentDataFromMsDevops ", e);
        }
    }

    private GithubService.GithubStatus calculateBlobStatus(String repo, String branch, Map<String, GithubService.GithubTreeItem> githubTree, Map<String, GithubService.GithubContent> localContents) {
        // absolute paths
        Sets.SetView<String> uniquePaths = Sets.union(localContents.keySet(), githubTree.keySet());
        return new GithubService.GithubStatus()
                .setSha(getLastCommitSha(repo, branch))
                .setFiles(uniquePaths.stream().collect(toMap(p -> p, p ->
                        calculateBlobStatus(githubTree.get(p), localContents.get(p))
                )));
    }

    public String getLastCommitSha(String repo, String branch) {
        try {
            var resp = azureDevOpsClient.getBranch(getAuthorization(),
                    getOrg(repo), getProj(repo), getProj(repo), branch);
            return JsonUtil.read(resp, "$.value[0].objectId");
        } catch (HttpClientError e) {
            if (e.getResponse().statusCode() == 404) {
                return null;
            }
            throw e;
        }
    }

    private String getOrg(String repo) {
        return Arrays.stream(repo.split("/")).limit(2).toList().get(0);
    }

    private String getProj(String repo) {
        return Arrays.stream(repo.split("/")).limit(2).toList().get(1);
    }

    private String calculateBlobStatus(GithubService.GithubTreeItem github, GithubService.GithubContent local) {
        if (github == null) {
            return GithubService.GithubStatus.A;
        }
        if (local == null) {
            return GithubService.GithubStatus.D;
        }
        if (!Objects.equals(github.getSha(), calculateSha(local))) {
            return GithubService.GithubStatus.M;
        }
        return GithubService.GithubStatus.U;
    }

    private String calculateSha(GithubService.GithubContent content) {
        if (content.getEncoding().equals(GithubService.GithubContent.GithubContentEncoding.base64)) {
            return calculateSha(Base64.getDecoder().decode(content.getContent()));
        }
        return calculateSha(content.getContent());
    }

    private String calculateSha(String src) {
        byte[] bytes = src.getBytes(StandardCharsets.UTF_8);
        return calculateSha(bytes);
    }

    private String calculateSha(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA1");
            md.update(String.format("%s %d\u0000", "blob", bytes.length).getBytes());
            md.update(bytes);
            return Hex.encodeHexString((md.digest()));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public GithubService.GithubContent getContent(String repo, String branch, String path) {
        try {
            var resp = azureDevOpsClient.getFileContent(getAuthorization(),
                    getOrg(repo), getProj(repo), getProj(repo), path, branch);
            resp.setSize(AzureToGithubMapper.extractSize(resp));
            if ("base64".equalsIgnoreCase(resp.getContentMetadata().getEncoding())) {
                resp.setSha(calculateSha(Base64.getDecoder().decode(resp.getContent())));
            } else {
                resp.setSha(calculateSha(resp.getContent()));
            }
            return AzureToGithubMapper.map(resp);
        } catch (HttpClientError e) {
            if (e.getResponse().statusCode() == 404) {
                return null;
            }
            throw e;
        }
    }

    public String getMainBranch(String repo) {
        Map<String, Object> repository = azureDevOpsClient.getRepository(getAuthorization(),
                getOrg(repo), getProj(repo), getProj(repo));
        String defaultRef = (String) repository.get("defaultBranch"); // e.g. "refs/heads/main"

        if (defaultRef != null && defaultRef.startsWith("refs/heads/")) {
            return defaultRef.substring("refs/heads/".length()); // strip prefix
        }
        return "main"; // fallback default
    }

    public List<String> listBranches(String repo) {
        Map<String, Object> response = azureDevOpsClient.listBranches(
                getAuthorization(),
                getOrg(repo), getProj(repo), getProj(repo)
        );

        List<Map<String, Object>> values = (List<Map<String, Object>>) response.get("value");
        if (values == null) {
            return Collections.emptyList();
        }

        return values.stream()
                .map(item -> (String) item.get("name"))
                .filter(Objects::nonNull)
                .map(name -> name.replaceFirst("^refs/heads/", "")) // remove "refs/heads/"
                .toList();
    }

    public Set<String> getExistingPaths(String repo, String branch) {
        AzureItemsResponse resp = azureDevOpsClient.getRepositoryItems(
                getAuthorization(),
                getOrg(repo), getProj(repo), getProj(repo),
                "/",          // scopePath root
                branch
        );
        return resp.getValue().stream()
                .filter(item -> "blob".equals(item.getGitObjectType()))
                .map(AzureItemsResponse.Item::getPath)
                .collect(Collectors.toSet());
    }

    public void commit(String repo, String branch, GithubService.GithubCommit c) {
        if (c.getLastCommitSha() == null) {
            c.setLastCommitSha(getLastCommitSha(repo, branch));
        }
        if (c.getLastCommitSha() == null) {
            c.setLastCommitSha(getLastCommitSha(repo, getMainBranch(repo)));
        }
        if (!listBranches(repo).contains(branch)) {
            List<Map<String, String>> body = List.of(Map.of(
                    "name", "refs/heads/" + branch,
                    "oldObjectId", "0000000000000000000000000000000000000000",
                    "newObjectId", c.getLastCommitSha()
            ));
            azureDevOpsClient.createBranch(getAuthorization(),
                    getOrg(repo), getProj(repo), getProj(repo), body);
        }

        Map<String, Object> refUpdate = Map.of(
                "name", "refs/heads/" + branch,
                "oldObjectId", c.getLastCommitSha()
        );

        final Set<String> existingPaths = getExistingPaths(repo, branch);

        List<Map<String, Object>> changes = c.getFiles().stream()
                .filter(f -> f.getContent() != null)
                .map(f -> Map.of(
                        "changeType", existingPaths.contains("/" + f.getPath()) ? "edit" : "add",
                        "item", Map.of("path", "/" + f.getPath()),
                        "newContent", Map.of(
                                "content", Base64.getEncoder().encodeToString(f.getContent().getBytes(StandardCharsets.UTF_8)),
                                "contentType", "base64Encoded"
                        )
                ))
                .toList();

        Map<String, Object> commit = Map.of(
                "comment", c.getMessage(),
                "author", Map.of(
                        "name", c.getAuthorName(),
                        "email", c.getAuthorEmail()
                ),
                "changes", changes
        );

        Map<String, Object> pushRequest = Map.of(
                "refUpdates", List.of(refUpdate),
                "commits", List.of(commit)
        );

        azureDevOpsClient.pushCommit(getAuthorization(), getOrg(repo), getProj(repo), getProj(repo), pushRequest);
    }

}
