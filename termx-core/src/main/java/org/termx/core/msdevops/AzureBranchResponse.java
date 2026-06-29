package org.termx.core.msdevops;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

@Getter
@Setter
@Accessors(chain = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AzureBranchResponse {
    private int count;
    private List<GitRef> value;

    @Getter
    @Setter
    @Accessors(chain = true)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GitRef {
        private String name;         // "refs/heads/main"
        private String objectId;     // commit SHA
        private String creator;      // optional
        private boolean isLocked;    // optional
        private String url;
    }
}