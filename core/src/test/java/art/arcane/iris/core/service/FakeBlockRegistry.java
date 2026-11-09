package art.arcane.iris.core.service;

import com.volmit.block.BlockRegistry;
import com.volmit.block.BlockState;
import com.volmit.block.BlockTurn;

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** A block registry that knows every block by its key and turns the {@code facing} of the blocks that have one. */
final class FakeBlockRegistry {
    private static final String FACING = "facing";
    private static final Map<String, double[]> FACING_DIRECTIONS = Map.of(
            "north", new double[]{0, 0, -1},
            "east", new double[]{1, 0, 0},
            "south", new double[]{0, 0, 1},
            "west", new double[]{-1, 0, 0});

    private FakeBlockRegistry() {
    }

    static BlockRegistry create() {
        BlockRegistry registry = mock(BlockRegistry.class);
        when(registry.find(anyString())).thenAnswer(invocation ->
                Optional.of(BlockState.parse(invocation.getArgument(0))));
        when(registry.rotate(any(), any())).thenAnswer(invocation ->
                rotated(invocation.getArgument(0), invocation.getArgument(1)));
        when(registry.tryRotate(any(), any())).thenAnswer(invocation ->
                Optional.of(rotated(invocation.getArgument(0), invocation.getArgument(1))));
        return registry;
    }

    static BlockState rotated(BlockState state, BlockTurn turn) {
        String facing = state.properties().get(FACING);
        if (facing == null) {
            return state;
        }
        double[] direction = FACING_DIRECTIONS.get(facing);
        BlockTurn.Direction turned = turn.turn(direction[0], direction[1], direction[2]);
        return state.withProperty(FACING, nameOf(turned));
    }

    private static String nameOf(BlockTurn.Direction direction) {
        String best = null;
        double bestDot = -Double.MAX_VALUE;
        for (Map.Entry<String, double[]> candidate : FACING_DIRECTIONS.entrySet()) {
            double[] vector = candidate.getValue();
            double dot = vector[0] * direction.x() + vector[2] * direction.z();
            if (dot > bestDot) {
                bestDot = dot;
                best = candidate.getKey();
            }
        }
        return best;
    }
}
