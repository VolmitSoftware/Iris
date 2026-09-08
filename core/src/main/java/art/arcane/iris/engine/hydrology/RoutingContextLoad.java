package art.arcane.iris.engine.hydrology;

import java.util.concurrent.CompletableFuture;

record RoutingContextLoad(Thread owner, CompletableFuture<SourceRoutingContext> future) {
}
