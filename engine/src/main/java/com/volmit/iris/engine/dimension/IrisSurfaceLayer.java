package com.volmit.iris.engine.dimension;

import com.google.gson.stream.JsonToken;
import com.volmit.iris.engine.editor.Resolvable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper=false)
@Accessors(fluent = true, chain = true)
@Resolvable.Entity(id = "surface-layer")
public class IrisSurfaceLayer extends IrisResolvable {
   @Builder.Default
   private IrisPalette palette = IrisPalette.flat("minecraft:stone");

   @Builder.Default
   private IrisRange thickness = IrisRange.flat(1);
}
