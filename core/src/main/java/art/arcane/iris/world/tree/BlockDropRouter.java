package art.arcane.iris.world.tree;

@FunctionalInterface
public interface BlockDropRouter {
    boolean routeDrop(Object drop);
}
