package art.arcane.iris.modded;

import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeStructureLocateResults;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.junit.Test;

import static org.junit.Assert.assertSame;

public class IrisModdedLocateRoutingTest {
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
}
