package com.volmit.iris.engine.dimension;

import com.volmit.iris.engine.editor.Resolvable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Singular;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
@EqualsAndHashCode(callSuper=false)
@Accessors(fluent = true, chain = true)
@Resolvable.Entity(id = "dimension")
public class IrisDimension  extends IrisResolvable {
    @Builder.Default
    private IrisDimensionMeta meta = new IrisDimensionMeta();

    @Singular
    @Type(IrisBiome.class)
    private List<IrisBiome> biomes = new ArrayList<>();
}
