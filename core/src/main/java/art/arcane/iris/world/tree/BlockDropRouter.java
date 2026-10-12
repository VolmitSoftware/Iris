package art.arcane.iris.world.tree;

import java.util.Collection;

public interface BlockDropRouter {
    Collection<?> captureNativeDrops();

    boolean routeDrop(Object drop);
}
