package art.arcane.iris.world.tree;

import art.arcane.iris.api.tree.TreeFellerAccess;
import art.arcane.iris.api.tree.TreeFellerRunHooks;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.decoration.tree.TreeBlockMaterial;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

final class TreeFellerModel {
    private TreeFellerModel() {
    }

    record PendingFell(
            TreeCandidate candidate,
            int preservationChance,
            TreeFellerRunHooks runHooks
    ) {
        PendingFell withAccess(TreeFellerAccess access) {
            return new PendingFell(candidate.withAccess(access), preservationChance, runHooks);
        }
    }

    record TreeCandidate(
            TreeContext context,
            World world,
            Player player,
            ItemStack tool,
            TreeFellerAccess access,
            TreeMarkerTraversal.Position trigger
    ) {
        TreeCandidate withAccess(TreeFellerAccess access) {
            return new TreeCandidate(context, world, player, tool, access, trigger);
        }
    }

    record TreeContext(
            Engine engine,
            String marker,
            TreeBlockMaterial expectedMaterial,
            int minimumY,
            int maximumY
    ) {
    }

    record TreeClaim(UUID worldId, String marker) {
    }

    record ProvenanceSnapshot(
            Engine engine,
            World world,
            int minimumY,
            TreeMarkerTraversal.Position position,
            String marker,
            TreeBlockMaterial material
    ) {
    }

    record ChunkPosition(int x, int z) {
    }

    record TreeMember(
            TreeMarkerTraversal.Position position,
            boolean log,
            TreeBlockMaterial expectedMaterial,
            int erosionOrder
    ) {
    }

    record DamageReservation(
            ItemStack toolForDrops,
            boolean charged,
            boolean broke,
            boolean logCostReserved
    ) {
    }
}
