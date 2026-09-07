package art.arcane.iris.engine.object;

import art.arcane.iris.spi.PlatformBlockState;

final class KeyedBlockState implements PlatformBlockState {
    private final String key;

    KeyedBlockState(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
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
        return false;
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
    public PlatformBlockState placementBaseState() {
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
    public boolean canPlaceOnto(PlatformBlockState onto) {
        return false;
    }

    @Override
    public boolean matches(PlatformBlockState state) {
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
    public PlatformBlockState withProperty(String name, String value) {
        return null;
    }

    @Override
    public Object nativeHandle() {
        return null;
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        return this == other;
    }

    @Override
    public String toString() {
        return key;
    }
}
