package com.kodality.termx.commons;

import jakarta.validation.constraints.Pattern;
import lombok.Getter;

@Getter
public class UniqueResource<T extends UniqueResource<T>> {
    /**
     * A unique identifier for the resource, which is used in URLs and references.
     * This ID must be unique within the context of the resource type.
     * It can contain letters, digits, hyphens, and periods, and must be between 1 and 64 characters in length.
     */
    @Pattern(regexp = "[A-Za-z0-9\\-\\.]{1,64}")
    private String id;

    public T setId(String id) {
        this.id = id;
        return (T) this;
    }
}
