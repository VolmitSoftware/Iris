package art.arcane.iris.engine.object;

import lombok.Builder;

import java.util.HashSet;
import java.util.List;

@Builder
public class PlacementLifecycleBuilder {
    HashSet<PlacementLifecycleDependency> dependencies;
    

}
