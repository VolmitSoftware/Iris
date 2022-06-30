package com.volmit.iris.engine.dimension;

import com.volmit.iris.engine.editor.Mutated;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(fluent = true, chain = true)
public class IrisBiome implements Mutated {
    private String name;
}
