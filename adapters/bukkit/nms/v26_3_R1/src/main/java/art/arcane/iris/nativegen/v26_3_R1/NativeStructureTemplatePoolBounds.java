package art.arcane.iris.nativegen.v26_3_R1;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.EmptyPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.FeaturePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.ListPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.alias.DirectPoolAlias;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasBinding;
import net.minecraft.world.level.levelgen.structure.pools.alias.RandomGroupPoolAlias;
import net.minecraft.world.level.levelgen.structure.pools.alias.RandomPoolAlias;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public final class NativeStructureTemplatePoolBounds {
    private NativeStructureTemplatePoolBounds() {
    }

    public static int sourceHorizontalSpan(RegistryAccess registryAccess,
                                           StructureTemplateManager templateManager,
                                           JigsawStructure source) {
        Registry<StructureTemplatePool> pools = registryAccess.lookupOrThrow(Registries.TEMPLATE_POOL);
        return sourceHorizontalSpan(
                templateManager,
                source.getStartPool(),
                source.getPoolAliases(),
                key -> pools.getValue(key.identifier()));
    }

    public static int horizontalSpan(RegistryAccess registryAccess,
                                     StructureTemplateManager templateManager,
                                     String templatePoolKey) {
        Identifier identifier = Identifier.tryParse(templatePoolKey);
        if (identifier == null) {
            throw new IllegalArgumentException("Invalid registered template pool key: " + templatePoolKey);
        }
        Registry<StructureTemplatePool> pools = registryAccess.lookupOrThrow(Registries.TEMPLATE_POOL);
        StructureTemplatePool pool = pools.getValue(identifier);
        if (pool == null) {
            throw new IllegalArgumentException("Registered template pool does not exist: " + templatePoolKey);
        }
        return horizontalSpan(pool, templateManager);
    }

    public static int horizontalSpan(RegistryAccess registryAccess,
                                     StructureTemplateManager templateManager,
                                     JigsawStructure source,
                                     String templatePoolKey) {
        Identifier identifier = Identifier.tryParse(templatePoolKey);
        if (identifier == null) {
            throw new IllegalArgumentException("Invalid registered template pool key: " + templatePoolKey);
        }
        Registry<StructureTemplatePool> pools = registryAccess.lookupOrThrow(Registries.TEMPLATE_POOL);
        ResourceKey<StructureTemplatePool> startPoolKey = ResourceKey.create(
                Registries.TEMPLATE_POOL, identifier);
        StructureTemplatePool startPool = pools.getValue(identifier);
        if (startPool == null) {
            throw new IllegalArgumentException("Registered template pool does not exist: " + templatePoolKey);
        }
        return sourceHorizontalSpan(
                templateManager,
                startPool,
                startPoolKey,
                source.getPoolAliases(),
                key -> pools.getValue(key.identifier()));
    }

    static int sourceHorizontalSpan(StructureTemplateManager templateManager,
                                    Holder<StructureTemplatePool> startPool,
                                    List<PoolAliasBinding> aliases,
                                    Function<ResourceKey<StructureTemplatePool>, StructureTemplatePool> poolLookup) {
        return sourceHorizontalSpan(templateManager, startPool.value(),
                startPool.unwrapKey().orElse(null), aliases, poolLookup);
    }

    static int sourceHorizontalSpan(StructureTemplateManager templateManager,
                                    StructureTemplatePool startPool,
                                    ResourceKey<StructureTemplatePool> startPoolKey,
                                    List<PoolAliasBinding> aliases,
                                    Function<ResourceKey<StructureTemplatePool>, StructureTemplatePool> poolLookup) {
        int maximumSpan = directHorizontalSpan(startPool, templateManager);
        if (startPoolKey == null) {
            return maximumSpan;
        }
        Set<ResourceKey<StructureTemplatePool>> targets = new HashSet<>();
        for (PoolAliasBinding binding : aliases) {
            collectTargets(binding, startPoolKey, targets);
        }
        for (ResourceKey<StructureTemplatePool> target : targets) {
            StructureTemplatePool targetPool = poolLookup.apply(target);
            if (targetPool == null) {
                throw new IllegalStateException("Jigsaw start-pool alias target does not exist: "
                        + target.identifier());
            }
            maximumSpan = Math.max(maximumSpan, directHorizontalSpan(targetPool, templateManager));
        }
        return maximumSpan;
    }

    private static void collectTargets(PoolAliasBinding binding,
                                       ResourceKey<StructureTemplatePool> startPoolKey,
                                       Set<ResourceKey<StructureTemplatePool>> targets) {
        if (binding instanceof DirectPoolAlias direct) {
            if (direct.alias().equals(startPoolKey)) {
                targets.add(direct.target());
            }
            return;
        }
        if (binding instanceof RandomPoolAlias random) {
            if (random.alias().equals(startPoolKey)) {
                random.targets().unwrap().forEach(entry -> targets.add(entry.value()));
            }
            return;
        }
        if (binding instanceof RandomGroupPoolAlias group) {
            group.groups().unwrap().forEach(entry -> entry.value().forEach(
                    nested -> collectTargets(nested, startPoolKey, targets)));
            return;
        }
        throw new IllegalStateException("Unsupported jigsaw pool alias type: "
                + binding.getClass().getName());
    }

    static int horizontalSpan(StructureTemplatePool pool,
                              StructureTemplateManager templateManager) {
        Set<StructureTemplatePool> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        return horizontalSpan(pool, templateManager, visited);
    }

    private static int horizontalSpan(StructureTemplatePool pool,
                                      StructureTemplateManager templateManager,
                                      Set<StructureTemplatePool> visited) {
        if (!visited.add(pool)) {
            return 0;
        }
        int maximumSpan = directHorizontalSpan(pool, templateManager);
        Holder<StructureTemplatePool> fallback = pool.getFallback();
        if (fallback == null || !fallback.isBound()) {
            throw new IllegalStateException("Template pool has an unresolved fallback");
        }
        return Math.max(maximumSpan, horizontalSpan(fallback.value(), templateManager, visited));
    }

    private static int directHorizontalSpan(StructureTemplatePool pool,
                                            StructureTemplateManager templateManager) {
        int maximumSpan = 0;
        for (Pair<StructurePoolElement, Integer> entry : pool.getTemplates()) {
            StructurePoolElement element = entry.getFirst();
            validateElement(element, templateManager);
            maximumSpan = Math.max(maximumSpan, horizontalSpan(element, templateManager));
        }
        return maximumSpan;
    }

    private static void validateElement(StructurePoolElement element,
                                        StructureTemplateManager templateManager) {
        if (element == EmptyPoolElement.INSTANCE || element instanceof FeaturePoolElement) {
            return;
        }
        if (element instanceof SinglePoolElement single) {
            Identifier template;
            try {
                template = single.getTemplateLocation();
            } catch (RuntimeException inlineTemplate) {
                return;
            }
            if (templateManager.get(template).isEmpty()) {
                throw new IllegalStateException("Template pool element does not resolve a structure template: "
                        + template);
            }
            return;
        }
        if (element instanceof ListPoolElement list) {
            for (StructurePoolElement child : list.getElements()) {
                validateElement(child, templateManager);
            }
            return;
        }
    }

    private static int horizontalSpan(StructurePoolElement element,
                                      StructureTemplateManager templateManager) {
        if (element == EmptyPoolElement.INSTANCE) {
            return 0;
        }
        long maximumSpan = 0L;
        for (Rotation rotation : Rotation.values()) {
            BoundingBox box;
            try {
                box = element.getBoundingBox(templateManager, BlockPos.ZERO, rotation);
            } catch (RuntimeException | LinkageError error) {
                throw new IllegalStateException("Template pool element "
                        + element.getClass().getName()
                        + " could not provide bounded geometry for rotation " + rotation, error);
            }
            if (box == null) {
                throw new IllegalStateException("Template pool element "
                        + element.getClass().getName()
                        + " returned no bounded geometry for rotation " + rotation);
            }
            long xSpan = (long) box.maxX() - box.minX() + 1L;
            long zSpan = (long) box.maxZ() - box.minZ() + 1L;
            maximumSpan = Math.max(maximumSpan, Math.max(xSpan, zSpan));
        }
        if (maximumSpan < 0L || maximumSpan > Integer.MAX_VALUE) {
            throw new IllegalStateException("Template pool element has an unbounded horizontal span");
        }
        return (int) maximumSpan;
    }
}
