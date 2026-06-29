package org.termx.core.msdevops;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AzureItemDetailResponse {
    private String objectId;
    private String gitObjectType;
    private String commitId;
    private String path;
    private String content; // base64 or raw depent of type
    private ContentMetadata contentMetadata;
    private Long size;
    private String url;
    private String sha;

    @Getter
    @Setter
    @Accessors(chain = true)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ContentMetadata {
        private String encoding; // for example "utf-8", "1252", "base64"
        private String contentType;
    }
}