package com.volmit.iris.engine.editor.pak;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Singular;
import lombok.experimental.Accessors;

import java.util.List;

@NoArgsConstructor
@Data
@AllArgsConstructor
@Builder
@Accessors(chain = true, fluent = true)
public class PakMetadata {
    private String namespace;
    @Singular
    private List<PakResourceMetadata> resources;
    private long pakSize;
}
