package art.arcane.iris.probe;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.platform.bukkit.nms.BukkitGenerationRegistry;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.PlatformGenerationRegistry;
import art.arcane.iris.spi.LogLevel;
import art.arcane.iris.spi.PlatformBiomeWriter;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.spi.PlatformScheduler;
import art.arcane.iris.spi.PlatformStructureHooks;
import art.arcane.volmlib.nativelib.entity.NativeEntityType;
import art.arcane.volmlib.nativelib.item.NativeItem;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.ModdedBlockState;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeBlockResolver;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeRegistryAccess;
import art.arcane.volmlib.nativelib.v26_3_R1.terrain.NativeGenerationRegistryImpl;
import art.arcane.volmlib.nativelib.v26_3_R1.terrain.NativeWorldGenerationImpl;
import art.arcane.volmlib.nativelib.terrain.NativeBiome;
import art.arcane.volmlib.nativelib.terrain.NativeBlockProperty;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

final class HeadlessNativePlatform implements IrisPlatform, PlatformRegistries, NativeBlockResolver.Policy {
    private final StubPlatform services;
    private final NativeRegistryAccess nativeAccess;
    private final PlatformBiomeWriter biomeWriter;
    private final PlatformGenerationRegistry generationRegistry;
    private final NativeBlockResolver blocks = new NativeBlockResolver(this);
    private final ConcurrentHashMap<String, NativeBlockState> states = new ConcurrentHashMap<>();

    HeadlessNativePlatform(File root, Supplier<HolderLookup.Provider> registries) {
        services = new StubPlatform(root);
        biomeWriter = new HeadlessNativeBiomeWriter(registries);
        generationRegistry = new BukkitGenerationRegistry(new NativeGenerationRegistryImpl(() -> {
            HolderLookup.Provider lookup = registries.get();
            if (lookup instanceof RegistryAccess access) {
                return access;
            }
            throw new IllegalStateException("Generation registry contract requires loaded native registries");
        }), "bukkit-generation-registry-v1", new NativeWorldGenerationImpl().generationRendererIdentity());
        nativeAccess = new NativeRegistryAccess(new NativeRegistryAccess.Configuration(registries,
                () -> { throw new UnsupportedOperationException("Headless reloadable registries are not loaded"); },
                message -> { throw new IllegalStateException("Unavailable native registry: " + message); }));
    }

    NativeBlockResolver blocks() {
        return blocks;
    }

    @Override
    public PlatformRegistries registries() {
        return this;
    }

    @Override
    public PlatformGenerationRegistry generationRegistry() {
        return generationRegistry;
    }

    @Override
    public String platformName() {
        return "headless-native-core";
    }

    @Override
    public String minecraftVersion() {
        return SharedConstants.getCurrentVersion().name();
    }

    @Override
    public NativeBlockState decodeBlockState(String key) {
        return states.computeIfAbsent(key, NativeBlockResolver::strictParse);
    }

    @Override
    public NativeBlockState block(String key) {
        return states.computeIfAbsent(key, candidate -> Objects.requireNonNull(blocks.getOrNull(candidate, true),
                "Unresolved native block: " + candidate));
    }

    @Override
    public NativeBlockState blockOrNull(String key) {
        return blocks.getOrNull(key);
    }

    @Override
    public NativeBlockState blockOrNull(String key, boolean warn) {
        return blocks.getOrNull(key, warn);
    }

    @Override
    public NativeBlockState air() {
        return blocks.getAir();
    }

    @Override
    public ModdedBlockState resolveCustomBlock(String key) {
        return null;
    }

    @Override
    public boolean preventLeafDecay() {
        return IrisSettings.get().getGenerator().isPreventLeafDecay();
    }

    @Override
    public void reportError(String key, Throwable failure) {
        services.reportError(failure);
        failure.printStackTrace(System.err);
    }

    @Override
    public void warnUnresolved(String key, String message) {
        throw new IllegalArgumentException(message + ": " + key);
    }

    @Override
    public void debug(String message) {
        services.log(LogLevel.DEBUG, message);
    }

    @Override
    public NativeBlockState deepSlateOre(NativeBlockState block, NativeBlockState ore) {
        return nativeAccess.deepSlateOre(block, ore);
    }

    @Override
    public NativeBiome biome(String key) {
        return nativeAccess.biome(key);
    }

    @Override
    public NativeItem item(String key) {
        return nativeAccess.item(key);
    }

    @Override
    public NativeEntityType entity(String key) {
        return nativeAccess.entity(key);
    }

    @Override
    public List<String> blockKeys() {
        return nativeAccess.blockKeys();
    }

    @Override
    public List<String> biomeKeys() {
        return nativeAccess.biomeKeys();
    }

    @Override
    public List<String> structureKeys() {
        return nativeAccess.structureKeys();
    }

    @Override
    public List<String> itemKeys() {
        return nativeAccess.itemKeys();
    }

    @Override
    public List<String> entityKeys() {
        return nativeAccess.entityKeys();
    }

    @Override
    public List<String> enchantmentKeys() {
        return nativeAccess.enchantmentKeys();
    }

    @Override
    public List<String> potionEffectKeys() {
        return nativeAccess.potionEffectKeys();
    }

    @Override
    public List<String> lootTableKeys() {
        return nativeAccess.lootTableKeys();
    }

    @Override
    public Map<String, List<NativeBlockProperty>> blockStateProperties() {
        return nativeAccess.blockStateProperties();
    }

    @Override
    public List<String> blockTypeKeys() {
        return blockKeys();
    }

    @Override
    public PlatformScheduler scheduler() {
        return services.scheduler();
    }

    @Override
    public PlatformStructureHooks structureHooks() {
        return services.structureHooks();
    }

    @Override
    public PlatformBiomeWriter biomeWriter() {
        return biomeWriter;
    }

    @Override
    public File dataFolder() {
        return services.dataFolder();
    }

    @Override
    public File dataFile(String... path) {
        return services.dataFile(path);
    }

    @Override
    public File pluginJar() {
        return services.pluginJar();
    }

    @Override
    public int irisVersionNumber() {
        return services.irisVersionNumber();
    }

    @Override
    public int minecraftVersionNumber() {
        return Integer.parseInt(minecraftVersion().replace(".", ""));
    }

    @Override
    public void callEvent(Object event) {
        services.callEvent(event);
    }

    @Override
    public void dispatchConsoleCommand(String command) {
        services.dispatchConsoleCommand(command);
    }

    @Override
    public boolean spawnEntity(NativeWorld world, String entityKey, double x, double y, double z) {
        return services.spawnEntity(world, entityKey, x, y, z);
    }

    @Override
    public void log(LogLevel level, String message) {
        services.log(level, message);
    }

    @Override
    public void msg(String message) {
        services.msg(message);
    }

    @Override
    public void reportError(Throwable error) {
        services.reportError(error);
    }

}
