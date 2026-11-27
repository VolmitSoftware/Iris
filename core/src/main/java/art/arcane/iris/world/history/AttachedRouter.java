package art.arcane.iris.world.history;

import java.util.Objects;

final class AttachedRouter {
    private volatile GenerationHistoryRuntimeRouter router;

    AttachedRouter(GenerationHistoryRuntimeRouter router) {
        this.router = Objects.requireNonNull(router, "generation-history runtime router");
    }

    void detach() {
        router = null;
    }

    GenerationHistoryRuntimeRouter attachedOrNull() {
        return router;
    }

    GenerationHistoryRuntimeRouter require() {
        GenerationHistoryRuntimeRouter attached = router;
        if (attached == null) {
            throw new IllegalStateException("Iris generation-history runtime router is detached.");
        }
        return attached;
    }
}
