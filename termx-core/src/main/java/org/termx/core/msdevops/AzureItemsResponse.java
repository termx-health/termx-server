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
public class AzureItemsResponse {
    private int count;
    private List<Item> value;

    @Getter
    @Setter
    @Accessors(chain = true)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        private String path;
        private String gitObjectType;
        private boolean isFolder;
        private String url;
    }
}