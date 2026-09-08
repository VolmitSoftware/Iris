package art.arcane.iris.engine.object;

@FunctionalInterface
  interface PlacementFunction<C> {
      void execute(C context);
  }
