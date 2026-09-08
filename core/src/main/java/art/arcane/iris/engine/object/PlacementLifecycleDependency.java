package art.arcane.iris.engine.object;

import lombok.Builder;

@Builder
public class PlacementLifecycleDependency {
    PlacementFunction<PlacementContext> function;


}
