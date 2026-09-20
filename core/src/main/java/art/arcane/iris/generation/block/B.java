package art.arcane.iris.generation.block;

import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.volmlib.util.collection.KList;

public class B {
    private static volatile BoundRegistries bound;

    private record BoundRegistries(IrisPlatform platform, PlatformRegistries registries) {
    }

    public static NativeBlockState getState(String bdxf) {
        return registries().block(bdxf);
    }

    public static NativeBlockState getStateOrNull(String bdxf) {
        return registries().blockOrNull(bdxf);
    }

    public static NativeBlockState getStateOrNull(String bdxf, boolean warn) {
        return registries().blockOrNull(bdxf, warn);
    }

    public static KList<NativeBlockState> getStates(KList<String> find) {
        KList<NativeBlockState> states = new KList<>(find.size());
        for (String key : find) {
            states.add(getState(key));
        }
        return states;
    }

    public static NativeBlockState getAirState() {
        return registries().air();
    }

    public static NativeBlockState toDeepSlateOre(NativeBlockState block, NativeBlockState ore) {
        return registries().deepSlateOre(block, ore);
    }

    public static boolean isAir(NativeBlockState state) {
        return state == null || state.isAir();
    }

    public static boolean isSolid(NativeBlockState state) {
        return state != null && state.isSolid();
    }

    public static boolean isOccluding(NativeBlockState state) {
        return state != null && state.isOccluding();
    }

    public static boolean isFluid(NativeBlockState state) {
        return state != null && state.isFluid();
    }

    public static boolean isAirOrFluid(NativeBlockState state) {
        return state == null || state.isAirOrFluid();
    }

    public static boolean isWater(NativeBlockState state) {
        return state != null && state.isWater();
    }

    public static boolean isWaterLogged(NativeBlockState state) {
        return state != null && state.isWaterLogged();
    }

    public static boolean isLit(NativeBlockState state) {
        return state != null && state.isLit();
    }

    public static boolean isUpdatable(NativeBlockState state) {
        return state != null && state.isUpdatable();
    }

    public static boolean isFoliage(NativeBlockState state) {
        return state != null && state.isFoliage();
    }

    public static boolean isTreeBlock(NativeBlockState state) {
        return state != null && state.isTreeBlock();
    }

    public static boolean isFoliagePlantable(NativeBlockState state) {
        return state != null && state.isFoliagePlantable();
    }

    public static boolean isDecorant(NativeBlockState state) {
        return state != null && state.isDecorant();
    }

    public static boolean isStorage(NativeBlockState state) {
        return state != null && state.isStorage();
    }

    public static boolean isStorageChest(NativeBlockState state) {
        return state != null && state.isStorageChest();
    }

    public static boolean isOre(NativeBlockState state) {
        return state != null && state.isOre();
    }

    public static boolean isDeepSlate(NativeBlockState state) {
        return state != null && state.isDeepSlate();
    }

    public static boolean isVineBlock(NativeBlockState state) {
        return state != null && state.isVineBlock();
    }

    public static boolean canPlaceOnto(NativeBlockState mat, NativeBlockState onto) {
        return mat != null && onto != null && mat.canPlaceOnto(onto);
    }

    public static boolean matches(NativeBlockState filter, NativeBlockState state) {
        return filter != null && state != null && filter.matches(state);
    }

    private static PlatformRegistries registries() {
        BoundRegistries cached = bound;
        if (cached != null && !IrisPlatforms.isBound()) {
            return cached.registries();
        }

        IrisPlatform platform = IrisPlatforms.get();
        if (cached != null && cached.platform() == platform) {
            return cached.registries();
        }

        BoundRegistries resolved = new BoundRegistries(platform, platform.registries());
        bound = resolved;
        return resolved.registries();
    }
}
