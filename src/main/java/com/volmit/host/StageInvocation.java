package com.volmit.host;

import com.volmit.IrisFeature;
import com.volmit.random.WorldRandom;
import com.volmit.stage.BlockStageContext;
import com.volmit.stage.Stage;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.IntStream;

/** Pipeline-independent state of one invocation: activation, settings, stage outputs and stage
 * random streams, all indexed by resolved position. Activation is computed once up front in resolved
 * order: a stage is active when its settings are configured (or it has no condition) and every
 * stage it requires is active, so inactivity cascades to dependents. Cost is one pass over stages
 * and requirement edges plus one settings lookup per distinct settings type. A stage's stream is
 * created when it first draws and depends only on the invocation seed and the stage id.
 */
final class StageInvocation {
    private static final Object PENDING = new Object();
    private static final int NO_STAGE = -1;

    private final ResolvedGraph graph;
    private final Object[] settingsBySlot;
    private final boolean[] active;
    private final Object[] outputs;
    private final long invocationSeed;
    private final WorldRandom[] streams;
    private int current = NO_STAGE;

    StageInvocation(ResolvedGraph graph, long invocationSeed, ConfiguredSettings settings) {
        this.graph = graph;
        settingsBySlot = lookUpSettings(graph, Objects.requireNonNull(settings, "settings"));
        active = activation(graph, settingsBySlot);
        outputs = new Object[graph.size()];
        Arrays.fill(outputs, PENDING);
        this.invocationSeed = invocationSeed;
        streams = new WorldRandom[graph.size()];
    }

    boolean isActive(int position) {
        return active[position];
    }

    int[] activePositions(Segment segment) {
        return IntStream.range(segment.start(), segment.end())
                .filter(position -> active[position])
                .toArray();
    }

    void runInvocationStage(int position, Object context) {
        if (!active[position]) {
            return;
        }
        var executable = graph.executable(position);
        var work = ((StageBody.Invocation) executable.body()).work();
        current = position;
        var output = work.run(context, settings(executable));
        current = NO_STAGE;
        complete(position, requireDeclaredOutput(executable, output));
    }

    Object newAccumulator(int position) {
        var initial = ((StageBody.Block) graph.executable(position).body()).initial();
        if (initial == null) {
            return null;
        }
        return Objects.requireNonNull(initial.get(), () -> "Stage " + graph.executable(position).stage()
                + " created a null accumulator");
    }

    void runBlockStage(int position, BlockStageContext context, Object accumulator) {
        var executable = graph.executable(position);
        var work = ((StageBody.Block) executable.body()).work();
        current = position;
        work.run(context, settings(executable), accumulator);
        current = NO_STAGE;
    }

    void complete(int position, Object output) {
        outputs[position] = output;
    }

    @SuppressWarnings("unchecked") // Bodies are typed by their stage, so the stored output matches O.
    <O> O output(Stage<O> stage) {
        Objects.requireNonNull(stage, "stage");
        var reader = currentStage();
        int position = reader.readablePosition(stage);
        if (position == ExecutableStage.NOT_READABLE) {
            throw new IllegalStateException("Stage " + reader.stage() + " read the output of " + stage
                    + " without requiring it; add requires(" + stage + ") to its definition");
        }
        if (outputs[position] == PENDING) {
            throw new IllegalStateException("Stage " + reader.stage() + " read the output of " + stage
                    + " before it completed; block stage outputs are available after their block pass");
        }
        return (O) outputs[position];
    }

    /** The running stage's own stream. A block stage keeps one stream across every block of its pass. */
    WorldRandom random() {
        if (current == NO_STAGE) {
            throw new IllegalStateException("A stage's random stream is only available while the stage runs");
        }
        if (streams[current] == null) {
            streams[current] = StageStreams.stream(invocationSeed, graph.executable(current).idHash());
        }
        return streams[current];
    }

    private ExecutableStage currentStage() {
        if (current == NO_STAGE) {
            throw new IllegalStateException("Stage outputs can only be read while a stage runs");
        }
        return graph.executable(current);
    }

    private Object settings(ExecutableStage executable) {
        int slot = executable.settingsSlot();
        return slot == ExecutableStage.NO_SETTINGS ? null : settingsBySlot[slot];
    }

    private static Object requireDeclaredOutput(ExecutableStage executable, Object output) {
        if (output == null && executable.stage().producesOutput()) {
            throw new NullPointerException("Stage " + executable.stage() + " produced a null output");
        }
        return output;
    }

    @SuppressWarnings("unchecked") // Settings types come from when(Class<? extends IrisFeature>).
    private static Object[] lookUpSettings(ResolvedGraph graph, ConfiguredSettings settings) {
        var types = graph.settingsTypes();
        var values = new Object[types.size()];
        for (int slot = 0; slot < values.length; slot++) {
            values[slot] = settings.find((Class<? extends IrisFeature>) types.get(slot)).orElse(null);
        }
        return values;
    }

    private static boolean[] activation(ResolvedGraph graph, Object[] settingsBySlot) {
        var active = new boolean[graph.size()];
        for (int position = 0; position < active.length; position++) {
            var executable = graph.executable(position);
            active[position] = isConfigured(executable, settingsBySlot) && requirementsActive(executable, active);
        }
        return active;
    }

    private static boolean isConfigured(ExecutableStage executable, Object[] settingsBySlot) {
        int slot = executable.settingsSlot();
        return slot == ExecutableStage.NO_SETTINGS || settingsBySlot[slot] != null;
    }

    private static boolean requirementsActive(ExecutableStage executable, boolean[] active) {
        for (int requirement : executable.requirements()) {
            if (!active[requirement]) {
                return false;
            }
        }
        return true;
    }
}
