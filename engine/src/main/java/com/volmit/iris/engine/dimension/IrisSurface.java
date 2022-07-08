package com.volmit.iris.engine.dimension;

import com.volmit.iris.engine.resolver.EngineResolvable;
import com.volmit.iris.engine.resolver.Resolvable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Singular;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper=false)
@Accessors(fluent = true, chain = true)
@Resolvable.Entity(id = "surface")
public class IrisSurface extends EngineResolvable {
    @Singular
    @Type(IrisSurfaceLayer.class)
    private List<IrisSurfaceLayer> layers = new ArrayList<>();

    @Singular
    @Type(IrisDecorator.class)
    private List<IrisDecorator> decorators = new ArrayList<>();
}
