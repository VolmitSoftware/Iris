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
@Resolvable.Entity(id = "surface-layer")
public class IrisSurfaceLayer extends EngineResolvable {
   @Builder.Default
   private IrisPalette palette = IrisPalette.flat("minecraft:stone");

   @Builder.Default
   private IrisRange thickness = IrisRange.flat(1);
}
