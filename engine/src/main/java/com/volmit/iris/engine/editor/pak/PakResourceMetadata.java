package com.volmit.iris.engine.editor.pak;

import com.volmit.iris.engine.editor.Resolvable;
import com.volmit.iris.platform.PlatformNamespaceKey;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@NoArgsConstructor
@Data
@AllArgsConstructor
@Builder
@Accessors(chain = true, fluent = true)
public class PakResourceMetadata
{
    private String type;
    private String key;
    private long start;
    private long length;
}
