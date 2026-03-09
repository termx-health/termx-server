package org.termx.ts.codesystem;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

@Setter
@Getter
@Introspected
@Accessors(chain = true)
public class ConceptSnapshot {
    public record SnapshotCoding(String system, String version, String code, String display) {}
    public record SnapshotProperty(String code, SnapshotCoding valueCoding, String conceptVersion, Long conceptVersionId) {}
    public record SnapshotDesignation(String use, String language, String name) {}
    private List<SnapshotProperty> properties;
    private List<SnapshotDesignation> designation;
}
