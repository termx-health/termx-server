package com.kodality.termx.ts;

import com.kodality.termx.commons.UniqueResource;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;

@Getter
public abstract class VersionReference<T extends VersionReference<T>> {
    private Long id;
    @Pattern(regexp = "[A-Za-z0-9\\-\\.]{1,64}")
    private String version;

    public T setId(Long id) {
        this.id = id;
        return (T) this;
    }

    public T setVersion(String version) {
        this.version = version;
        return (T) this;
    }
}
