package org.termx.core.msdevops;

import org.termx.core.github.GithubService;

import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class AzureToGithubMapper {

    public static GithubService.GithubTree map(AzureItemsResponse azureItems, String rootSha) {
        if (Objects.isNull(azureItems)) {
            return null;
        }

        GithubService.GithubTree githubTree = new GithubService.GithubTree();
        githubTree.setSha(rootSha);

        List<GithubService.GithubTreeItem> treeItems = azureItems.getValue().stream()
                .map(AzureToGithubMapper::mapItem)
                .collect(Collectors.toList());

        githubTree.setTree(treeItems);
        return githubTree;
    }

    public static GithubService.GithubContent map(AzureItemDetailResponse azureDetail) {
        if (Objects.isNull(azureDetail)) {
            return null;
        }
        GithubService.GithubContent github = new GithubService.GithubContent()
                .setPath(stripLeadingSlash(azureDetail.getPath()))
                .setGitUrl(azureDetail.getUrl())
                .setSha(azureDetail.getObjectId())
                .setSize(azureDetail.getSize())
                .setContent(azureDetail.getContent())
                .setEncoding(mapEncoding(azureDetail.getContentMetadata()));
        github.setDownloadUrl(null);
        if ("base64".equalsIgnoreCase(github.getEncoding())) {
            github.setContent(new String(Base64.getDecoder().decode(github.getContent().replaceAll("\n", ""))));
        }
        return github;
    }

    private static String mapEncoding(AzureItemDetailResponse.ContentMetadata metadata) {
        if (metadata != null && "base64".equalsIgnoreCase(metadata.getEncoding())) {
            return GithubService.GithubContent.GithubContentEncoding.base64;
        }
        return GithubService.GithubContent.GithubContentEncoding.utf8;
    }

    private static GithubService.GithubTreeItem mapItem(AzureItemsResponse.Item azureItem) {
        GithubService.GithubTreeItem item = new GithubService.GithubTreeItem();

        item.setPath(stripLeadingSlash(azureItem.getPath()));
        item.setType(mapType(azureItem.getGitObjectType()));
        item.setMode(mapMode(azureItem.getGitObjectType()));
        item.setSha(null);
        item.setUrl(azureItem.getUrl());
        item.setSize(null);

        return item;
    }

    private static String stripLeadingSlash(String path) {
        if (path == null) return null;
        return path.startsWith("/") ? path.substring(1) : path;
    }

    private static String mapType(String azureType) {
        if ("blob".equalsIgnoreCase(azureType)) {
            return "blob";
        } else if ("tree".equalsIgnoreCase(azureType)) {
            return "tree";
        } else if ("commit".equalsIgnoreCase(azureType)) {
            return "commit";
        }
        // Default fallback
        return "blob";
    }

    private static String mapMode(String azureType) {
        return switch (azureType.toLowerCase()) {
            case "blob" -> "100644";  // regular file
            case "tree" -> "040000";  // directory
            case "commit" -> "160000";  // submodule
            default -> "100644";
        };
    }

    public static Long extractSize(AzureItemDetailResponse detail) {
        if (detail.getContent() != null) {
            if ("base64".equalsIgnoreCase(detail.getContentMetadata().getEncoding())) {
                byte[] decoded = Base64.getDecoder().decode(detail.getContent());
                return (long) decoded.length;
            } else {
                return (long) detail.getContent().length(); // characters length
            }
        }
        return null;
    }

}