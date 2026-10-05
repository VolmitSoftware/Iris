package com.volmit.host;

import com.volmit.resource.ResourceId;
import com.volmit.stage.Stage;
import com.volmit.stage.StageRef;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Diagnostic view of one stage at its resolved position. incoming lists every edge into this
 * stage, including soft edges other stages declared; absentSoftTargets lists this stage's soft
 * references that matched no installed stage and therefore add no ordering.
 */
public record ResolvedStage(
        Stage<?> stage,
        ResourceId module,
        Optional<FeatureDescriptor<?>> settings,
        List<StageEdge> incoming,
        List<StageRef> absentSoftTargets) {
    public ResolvedStage {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(settings, "settings");
        incoming = List.copyOf(incoming);
        absentSoftTargets = List.copyOf(absentSoftTargets);
    }
}
