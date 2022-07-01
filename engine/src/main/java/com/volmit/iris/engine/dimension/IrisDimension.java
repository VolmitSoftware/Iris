package com.volmit.iris.engine.dimension;

import com.volmit.iris.engine.editor.Resolvable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
@Accessors(fluent = true, chain = true)
public class IrisDimension implements Resolvable
{
    @Builder.Default
    private IrisDimensionMeta meta = new IrisDimensionMeta();
}
