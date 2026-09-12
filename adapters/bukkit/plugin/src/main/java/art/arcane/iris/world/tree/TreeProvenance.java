package art.arcane.iris.world.tree;

import art.arcane.iris.api.tree.TreeFellerAccess;
import art.arcane.iris.world.tree.TreeFellerModel.ProvenanceSnapshot;
import art.arcane.iris.world.tree.TreeFellerModel.TreeCandidate;
import art.arcane.iris.world.tree.TreeFellerModel.TreeContext;
import art.arcane.iris.world.IrisToolbelt;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.structure.placement.StructurePlacementMarker;
import art.arcane.iris.generation.decoration.tree.TreeBlockMaterial;
import art.arcane.iris.platform.generation.PlatformChunkGenerator;
import org.bukkit.GameMode;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Objects;

final class TreeProvenance {
    private final Map<Engine, TreeDefinitionIndex> definitions;

    TreeProvenance(Map<Engine, TreeDefinitionIndex> definitions) {
        this.definitions = definitions;
    }

    TreeCandidate resolveCandidate(Block block, Player player) {
        if (player.getGameMode() != GameMode.SURVIVAL || !player.isSneaking()) {
            return null;
        }
        if (!Tag.LOGS.isTagged(block.getType())) {
            return null;
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!TreeFellerSVC.isAxe(tool)) {
            return null;
        }
        TreeContext context = resolveTreeContext(block);
        if (context == null) {
            return null;
        }
        return new TreeCandidate(
                context,
                block.getWorld(),
                player,
                tool.clone(),
                TreeFellerAccess.STANDALONE,
                new TreeMarkerTraversal.Position(block.getX(), block.getY(), block.getZ())
        );
    }

    TreeContext resolveTreeContext(Block block) {
        if (block.getType().isAir()) {
            return null;
        }
        PlatformChunkGenerator access = IrisToolbelt.access(block.getWorld());
        if (access == null || access.getEngine() == null) {
            return null;
        }
        Engine engine = access.getEngine();
        World world = block.getWorld();
        int minimumY = world.getMinHeight();
        int maximumY = world.getMaxHeight();
        int relativeY = block.getY() - minimumY;
        String marker = markerAt(engine, minimumY, block.getX(), block.getY(), block.getZ());
        StructurePlacementMarker.Decoded decoded = StructurePlacementMarker.decode(marker);
        if (decoded == null || decoded.structureAware()) {
            return null;
        }
        TreeBlockMaterial expected = engine.getMantle().getMantle().get(
                block.getX(),
                relativeY,
                block.getZ(),
                TreeBlockMaterial.class
        );
        if (expected != null && !matchesExpectedMaterial(block, expected)) {
            return null;
        }
        if (expected == null
                && !decoded.objectKey().startsWith("trees/")
                && !definitionIndex(engine).isTreeMarker(marker)) {
            return null;
        }
        return new TreeContext(engine, marker, expected, minimumY, maximumY);
    }

    TreeDefinitionIndex definitionIndex(Engine engine) {
        synchronized (definitions) {
            return definitions.computeIfAbsent(engine, TreeDefinitionIndex::build);
        }
    }

    String markerAt(Engine engine, int minimumY, TreeMarkerTraversal.Position position) {
        return markerAt(engine, minimumY, position.x(), position.y(), position.z());
    }

    String markerAt(Engine engine, int minimumY, int x, int y, int z) {
        return engine.getMantle().getMantle().get(x, y - minimumY, z, String.class);
    }

    TreeBlockMaterial materialAt(Engine engine, int minimumY, TreeMarkerTraversal.Position position) {
        return engine.getMantle().getMantle().get(
                position.x(),
                position.y() - minimumY,
                position.z(),
                TreeBlockMaterial.class
        );
    }

    ProvenanceSnapshot captureProvenance(Block block) {
        PlatformChunkGenerator access = IrisToolbelt.access(block.getWorld());
        if (access == null || access.getEngine() == null) {
            return null;
        }
        Engine engine = access.getEngine();
        TreeMarkerTraversal.Position position = new TreeMarkerTraversal.Position(
                block.getX(),
                block.getY(),
                block.getZ()
        );
        int minimumY = block.getWorld().getMinHeight();
        String marker = markerAt(engine, minimumY, position);
        TreeBlockMaterial material = materialAt(engine, minimumY, position);
        if (marker == null && material == null) {
            return null;
        }
        return new ProvenanceSnapshot(engine, block.getWorld(), minimumY, position, marker, material);
    }

    void clearProvenanceIfMatching(ProvenanceSnapshot snapshot) {
        String marker = markerAt(snapshot.engine(), snapshot.minimumY(), snapshot.position());
        TreeBlockMaterial material = materialAt(snapshot.engine(), snapshot.minimumY(), snapshot.position());
        if (Objects.equals(snapshot.marker(), marker) && Objects.equals(snapshot.material(), material)) {
            clearProvenance(snapshot.engine(), snapshot.minimumY(), snapshot.position());
        }
    }

    void clearProvenance(Engine engine, int minimumY, TreeMarkerTraversal.Position position) {
        int relativeY = position.y() - minimumY;
        engine.getMantle().getMantle().remove(position.x(), relativeY, position.z(), String.class);
        engine.getMantle().getMantle().remove(position.x(), relativeY, position.z(), TreeBlockMaterial.class);
    }

    boolean matchesExpectedMaterial(Block block, TreeBlockMaterial expected) {
        return expected.matches(block.getBlockData().getAsString());
    }
}
