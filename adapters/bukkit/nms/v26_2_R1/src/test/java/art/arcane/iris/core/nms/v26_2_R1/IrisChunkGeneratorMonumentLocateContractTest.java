package art.arcane.iris.core.nms.v26_2_R1;

import art.arcane.iris.nativegen.NativeStructureLocateResults;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class IrisChunkGeneratorMonumentLocateContractTest {
    @Test
    public void mixedLocateSelectsNearestProviderAndPrefersNativeOnTie() {
        BlockPos origin = BlockPos.ZERO;
        Pair<BlockPos, Holder<Structure>> irisNear = Pair.of(new BlockPos(4, 70, 0), null);
        Pair<BlockPos, Holder<Structure>> nativeFar = Pair.of(new BlockPos(8, 70, 0), null);
        Pair<BlockPos, Holder<Structure>> nativeNear = Pair.of(new BlockPos(2, 70, 0), null);
        Pair<BlockPos, Holder<Structure>> nativeTie = Pair.of(new BlockPos(0, 70, 4), null);

        assertSame(irisNear, NativeStructureLocateResults.nearest(origin, irisNear, nativeFar));
        assertSame(nativeNear, NativeStructureLocateResults.nearest(origin, irisNear, nativeNear));
        assertSame(nativeTie, NativeStructureLocateResults.nearest(origin, irisNear, nativeTie));
    }

    @Test
    public void mixedUnexploredLocateReferencesOnlyTheSelectedProvider() {
        BlockPos origin = BlockPos.ZERO;
        Pair<BlockPos, Holder<Structure>> irisNear = Pair.of(new BlockPos(4, 70, 0), null);
        Pair<BlockPos, Holder<Structure>> nativeFar = Pair.of(new BlockPos(8, 70, 0), null);
        AtomicInteger irisReferences = new AtomicInteger();
        AtomicInteger nativeReferences = new AtomicInteger();

        Pair<BlockPos, Holder<Structure>> irisSelected =
                NativeStructureLocateResults.selectAndReference(
                        origin,
                        irisNear, () -> irisReferences.incrementAndGet(),
                        nativeFar, () -> nativeReferences.incrementAndGet());

        assertSame(irisNear, irisSelected);
        assertEquals(1, irisReferences.get());
        assertEquals(0, nativeReferences.get());

        Pair<BlockPos, Holder<Structure>> nativeNear = Pair.of(new BlockPos(2, 70, 0), null);
        Pair<BlockPos, Holder<Structure>> nativeSelected =
                NativeStructureLocateResults.selectAndReference(
                        origin,
                        irisNear, () -> irisReferences.incrementAndGet(),
                        nativeNear, () -> nativeReferences.incrementAndGet());

        assertSame(nativeNear, nativeSelected);
        assertEquals(1, irisReferences.get());
        assertEquals(1, nativeReferences.get());
    }
}
