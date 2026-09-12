/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.modded;

import art.arcane.iris.platform.bukkit.nms.datapack.DataVersion;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.volmlib.util.json.JSONObject;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.dimension.DimensionType;

final class WorldCheckDimensionContract {

    private WorldCheckDimensionContract() {
    }

    static boolean checkDimensionType(ServerLevel level, IrisModdedChunkGenerator generator) {
        try {
            IrisDimension dimension = generator.commandEngine().getDimension();
            DimensionContract expected = expectedDimensionContract(dimension);
            DimensionContract actual = runtimeDimensionContract(level.dimensionType());
            boolean pass = matchesDimensionContract(level.getMinY(), level.getHeight(), expected, actual);
            String detail = "expected=" + expected + ",actual=" + actual
                    + ",levelMinY=" + level.getMinY() + ",levelHeight=" + level.getHeight();
            WorldCheckPredicates.qaEvent("dimension_type", dimension.getLoadKey(), pass, detail);
            if (!pass) {
                ModdedIrisLog.error("[worldcheck] dimension type mismatch for {}: {}", dimension.getLoadKey(), detail);
            } else {
                ModdedIrisLog.info("[worldcheck] dimension type contract: {}", detail);
            }
            return pass;
        } catch (Throwable error) {
            ModdedIrisLog.error("[worldcheck] could not validate the Iris dimension type contract", error);
            WorldCheckPredicates.qaEvent("dimension_type", generator.activeDimensionKey(), false,
                    "validationError=" + error.getClass().getSimpleName() + ":" + error.getMessage());
            return false;
        }
    }

    static DimensionContract expectedDimensionContract(IrisDimension dimension) {
        JSONObject json = new JSONObject(dimension.getDimensionType().toJson(DataVersion.getLatest().get()));
        return new DimensionContract(
                json.getInt("min_y"),
                json.getInt("height"),
                json.getInt("logical_height"),
                json.getDouble("coordinate_scale"),
                (float) json.getDouble("ambient_light"),
                json.getBoolean("has_skylight"),
                json.getBoolean("has_ceiling"),
                json.getBoolean("has_ender_dragon_fight"),
                json.getInt("monster_spawn_block_light_limit"));
    }

    static DimensionContract runtimeDimensionContract(DimensionType dimensionType) {
        return new DimensionContract(
                dimensionType.minY(),
                dimensionType.height(),
                dimensionType.logicalHeight(),
                dimensionType.coordinateScale(),
                dimensionType.ambientLight(),
                dimensionType.hasSkyLight(),
                dimensionType.hasCeiling(),
                dimensionType.hasEnderDragonFight(),
                dimensionType.monsterSpawnBlockLightLimit());
    }

    static boolean matchesDimensionContract(int levelMinY, int levelHeight,
                                            DimensionContract expected, DimensionContract actual) {
        return levelMinY == expected.minY()
                && levelHeight == expected.height()
                && actual.equals(expected);
    }

    static boolean checkEntityMixins(ServerLevel level) {
        ItemEntity item = new ItemEntity(level, 0D, level.getMinY(), 0D, Items.COBBLESTONE.getDefaultInstance());
        boolean vanillaSave = item.shouldBeSaved();
        ModdedEntityPersistence.configure(item, false);
        boolean suppressed = !item.shouldBeSaved();
        ModdedEntityPersistence.configure(item, true);
        boolean restored = item.shouldBeSaved();
        boolean pass = vanillaSave && suppressed && restored;
        WorldCheckPredicates.qaEvent("entity_mixin", "persistence", pass,
                "vanilla=" + vanillaSave + ",suppressed=" + suppressed + ",restored=" + restored);
        if (!pass) {
            ModdedIrisLog.error("[worldcheck] shared entity mixins are not active on this loader");
        }
        return pass;
    }

    record DimensionContract(int minY, int height, int logicalHeight, double coordinateScale,
                             float ambientLight, boolean hasSkyLight, boolean hasCeiling,
                             boolean hasEnderDragonFight, int monsterSpawnBlockLightLimit) {
    }
}
