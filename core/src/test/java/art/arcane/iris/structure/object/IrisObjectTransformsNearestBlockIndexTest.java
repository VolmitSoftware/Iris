package art.arcane.iris.structure.object;

import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.iris.generation.block.B;
import art.arcane.iris.generation.block.VectorMap;
import art.arcane.iris.generation.geometry.IrisBlockVector;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.assertSame;

public class IrisObjectTransformsNearestBlockIndexTest {
    @Test
    public void spatialLookupMatchesLinearIterationIncludingDistanceTies() {
        VectorMap<NativeBlockState> blocks = new VectorMap<>();
        Random random = new Random(812734L);
        for (int i = 0; i < 120; i++) {
            NativeBlockState state = new SolidBlockState(false);
            blocks.put(new IrisBlockVector(
                    random.nextInt(25) - 12,
                    random.nextInt(25) - 12,
                    random.nextInt(25) - 12
            ), state);
        }

        NativeBlockState explicitAir = new SolidBlockState(true);
        blocks.put(new IrisBlockVector(0, 0, 0), explicitAir);
        IrisObjectTransforms.NearestBlockIndex index = IrisObjectTransforms.NearestBlockIndex.create(blocks);
        List<Map.Entry<IrisBlockVector, NativeBlockState>> candidates = new ArrayList<>();
        for (Map.Entry<IrisBlockVector, NativeBlockState> entry : blocks) {
            candidates.add(entry);
        }

        for (int x = -10; x <= 10; x++) {
            for (int y = -10; y <= 10; y++) {
                for (int z = -10; z <= 10; z++) {
                    IrisBlockVector query = new IrisBlockVector(x, y, z);
                    NativeBlockState direct = blocks.get(query);
                    NativeBlockState actual = B.isAir(direct) ? index.nearest(x, y, z, direct) : direct;
                    assertSame("Mismatch at " + x + "," + y + "," + z,
                            nearestByLinearIteration(blocks, candidates, query), actual);
                }
            }
        }
    }

    @Test
    public void emptyIndexRetainsMissingAndExplicitAirFallbacks() {
        VectorMap<NativeBlockState> blocks = new VectorMap<>();
        NativeBlockState explicitAir = new SolidBlockState(true);
        blocks.put(new IrisBlockVector(1, 2, 3), explicitAir);
        IrisObjectTransforms.NearestBlockIndex index = IrisObjectTransforms.NearestBlockIndex.create(blocks);

        assertSame(explicitAir, index.nearest(1, 2, 3, explicitAir));
        assertSame(null, index.nearest(4, 5, 6, null));
    }

    private static NativeBlockState nearestByLinearIteration(
            VectorMap<NativeBlockState> blocks,
            List<Map.Entry<IrisBlockVector, NativeBlockState>> candidates,
            IrisBlockVector query
    ) {
        NativeBlockState result = blocks.get(query);
        if (!B.isAir(result)) {
            return result;
        }

        double nearestDistance = Double.MAX_VALUE;
        for (Map.Entry<IrisBlockVector, NativeBlockState> entry : candidates) {
            NativeBlockState state = entry.getValue();
            if (B.isAir(state)) {
                continue;
            }

            double distance = entry.getKey().distanceSquared(query);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                result = state;
            }
        }
        return result;
    }

    private static final class SolidBlockState implements NativeBlockState {
        private final boolean air;

        private SolidBlockState(boolean air) {
            this.air = air;
        }

        @Override
        public String key() {
            return null;
        }

        @Override
        public String namespace() {
            return null;
        }

        @Override
        public String materialKey() {
            return null;
        }

        @Override
        public boolean isAir() {
            return air;
        }

        @Override
        public boolean isSolid() {
            return false;
        }

        @Override
        public boolean isOccluding() {
            return false;
        }

        @Override
        public boolean isCustom() {
            return false;
        }

        @Override
        public String deferredPlacementKey() {
            return null;
        }

        @Override
        public NativeBlockState placementBaseState() {
            return null;
        }

        @Override
        public boolean isFluid() {
            return false;
        }

        @Override
        public boolean isWater() {
            return false;
        }

        @Override
        public boolean isWaterLogged() {
            return false;
        }

        @Override
        public boolean isLit() {
            return false;
        }

        @Override
        public boolean isUpdatable() {
            return false;
        }

        @Override
        public boolean isFoliage() {
            return false;
        }

        @Override
        public boolean isTreeBlock() {
            return false;
        }

        @Override
        public boolean isFoliagePlantable() {
            return false;
        }

        @Override
        public boolean isDecorant() {
            return false;
        }

        @Override
        public boolean isStorage() {
            return false;
        }

        @Override
        public boolean isStorageChest() {
            return false;
        }

        @Override
        public boolean isOre() {
            return false;
        }

        @Override
        public boolean isDeepSlate() {
            return false;
        }

        @Override
        public boolean isVineBlock() {
            return false;
        }

        @Override
        public boolean canPlaceOnto(NativeBlockState onto) {
            return false;
        }

        @Override
        public boolean matches(NativeBlockState state) {
            return false;
        }

        @Override
        public boolean isAirOrFluid() {
            return false;
        }

        @Override
        public boolean hasTileEntity() {
            return false;
        }

        @Override
        public NativeBlockState withProperty(String name, String value) {
            return null;
        }

        @Override
        public Object nativeHandle() {
            return null;
        }
    }
}
