package art.arcane.iris.generation.hydrology;

import java.util.concurrent.CompletableFuture;

record RoutingContextLoad(Thread owner, CompletableFuture<SourceRoutingContext> future) {
}
