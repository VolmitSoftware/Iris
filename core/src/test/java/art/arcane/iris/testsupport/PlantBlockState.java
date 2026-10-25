package art.arcane.iris.testsupport;

import art.arcane.volmlib.nativelib.terrain.NativeBlockState;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

public final class PlantBlockState implements NativeBlockState {
    private static final Map<String, PlantBlockState> STATES = new ConcurrentHashMap<>();
    private static final Set<String> PLANTS = Set.of("minecraft:seagrass", "minecraft:tall_seagrass", "minecraft:kelp", "minecraft:kelp_plant",
            "minecraft:sugar_cane", "minecraft:fern", "minecraft:short_grass", "minecraft:tall_grass", "minecraft:large_fern",
            "minecraft:lily_pad", "minecraft:sea_pickle", "minecraft:tube_coral");
    private static final Set<String> CANE_GROUND = Set.of("minecraft:dirt", "minecraft:sand", "minecraft:grass_block", "minecraft:sugar_cane");

    private final String key;
    private final String block;
    private final TreeMap<String, String> properties;

    private PlantBlockState(String key, String block, TreeMap<String, String> properties) {
        this.key = key;
        this.block = block;
        this.properties = properties;
    }

    public static PlantBlockState of(String key) {
        int bracket = key.indexOf('[');
        String block = bracket < 0 ? key : key.substring(0, bracket);
        TreeMap<String, String> properties = new TreeMap<>();
        if (bracket >= 0) {
            for (String pair : key.substring(bracket + 1, key.length() - 1).split(",")) {
                String[] parts = pair.split("=");
                properties.put(parts[0], parts[1]);
            }
        }
        return of(block, properties);
    }

    private static PlantBlockState of(String block, TreeMap<String, String> properties) {
        StringBuilder key = new StringBuilder(block);
        if (!properties.isEmpty()) {
            key.append('[');
            boolean first = true;
            for (Map.Entry<String, String> property : properties.entrySet()) {
                key.append(first ? "" : ",").append(property.getKey()).append('=').append(property.getValue());
                first = false;
            }
            key.append(']');
        }
        String built = key.toString();
        return STATES.computeIfAbsent(built, ignored -> new PlantBlockState(built, block, properties));
    }

    public String block() {
        return block;
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public String namespace() {
        return "minecraft";
    }

    @Override
    public String materialKey() {
        return block;
    }

    @Override
    public boolean isAir() {
        return block.endsWith("air");
    }

    @Override
    public boolean isSolid() {
        return !isAir() && !isFluid() && !PLANTS.contains(block);
    }

    @Override
    public boolean isOccluding() {
        return isSolid();
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
        return this;
    }

    @Override
    public boolean isFluid() {
        return block.equals("minecraft:water") || block.equals("minecraft:lava");
    }

    @Override
    public boolean isWater() {
        return block.equals("minecraft:water");
    }

    @Override
    public boolean isWaterLogged() {
        return "true".equals(properties.get("waterlogged"));
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
        if (!block.equals("minecraft:sugar_cane")) {
            return onto != null && onto.isSolid();
        }
        return onto instanceof PlantBlockState plant && CANE_GROUND.contains(plant.block);
    }

    @Override
    public boolean matches(NativeBlockState state) {
        return state == this;
    }

    @Override
    public boolean isAirOrFluid() {
        return isAir() || isFluid();
    }

    @Override
    public boolean hasTileEntity() {
        return false;
    }

    @Override
    public NativeBlockState withProperty(String name, String value) {
        TreeMap<String, String> changed = new TreeMap<>(properties);
        changed.put(name, value);
        return of(block, changed);
    }

    @Override
    public Object nativeHandle() {
        return key;
    }

    @Override
    public String toString() {
        return key;
    }
}
