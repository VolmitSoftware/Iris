package com.volmit.iris.engine.dimension;

import com.volmit.iris.engine.resolver.EngineResolvable;
import com.volmit.iris.engine.resolver.Resolvable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper=false)
@Accessors(fluent = true, chain = true)
@Resolvable.Entity(id = "decorator")
public class IrisDecorator extends EngineResolvable {
    @Builder.Default
    private IrisPalette palette = IrisPalette.flat("minecraft:grass");

    @Builder.Default
    private IrisChance chance = IrisChance.white(0.25);
}
