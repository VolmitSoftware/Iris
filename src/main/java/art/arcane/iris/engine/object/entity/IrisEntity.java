/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.engine.object.entity;

import art.arcane.iris.engine.object.entity.spawn.IrisSurface;
import art.arcane.iris.engine.object.loot.IrisLoot;
import art.arcane.iris.engine.object.loot.IrisLootReference;
import art.arcane.iris.engine.object.marker.IrisCommand;
import art.arcane.iris.core.loader.IrisRegistrant;
import art.arcane.iris.engine.object.annotations.ArrayType;
import art.arcane.iris.engine.object.annotations.Desc;
import art.arcane.iris.engine.object.annotations.RegistryListEntityType;
import art.arcane.iris.engine.object.annotations.RegistryListSpecialEntity;
import art.arcane.iris.engine.object.annotations.Required;
import art.arcane.volmlib.util.collection.KList;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@SuppressWarnings("ALL")
@Accessors(chain = true)
@NoArgsConstructor

@Desc("Represents an iris entity.")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisEntity extends IrisRegistrant {
    @Required
    @RegistryListEntityType
    @Desc("The namespaced key of the entity type to spawn, such as minecraft:zombie or alexsmobs:grizzly_bear. To spawn a mob from another plugin or provider, set this type to unknown and define the special type.")
    private String type = null;

    @Desc("The SpawnReason name to spawn the entity with, such as NATURAL or COMMAND.")
    private String reason = null;

    @Desc("The custom name of this entity")
    private String customName = "";

    @Desc("Should the name on this entity be visible even if you arent looking at it.")
    private boolean customNameVisible = false;

    @Desc("If this entity type is a mob, should it be aware of it's surroundings & interact with the world.")
    private boolean aware = true;

    @Desc("If this entity type is a creature, should it have ai goals.")
    private boolean ai = true;

    @Desc("Should this entity be glowing")
    private boolean glowing = false;

    @Desc("Should gravity apply to this entity")
    private boolean gravity = true;

    @Desc("When an entity is invulnerable it can only be damaged by players increative mode.")
    private boolean invulnerable = false;

    @Desc("When an entity is silent it will not produce any sound.")
    private boolean silent = false;

    @Desc("Should this entity be allowed to pickup items")
    private boolean pickupItems = false;

    @Desc("Should this entity be removed when far away")
    private boolean removable = false;

    @Desc("Entity helmet equipment")
    private IrisLoot helmet = null;

    @Desc("Entity chestplate equipment")
    private IrisLoot chestplate = null;

    @Desc("Entity boots equipment")
    private IrisLoot boots = null;

    @Desc("Entity leggings equipment")
    private IrisLoot leggings = null;

    @Desc("Entity main hand equipment")
    private IrisLoot mainHand = null;

    @Desc("Entity off hand equipment")
    private IrisLoot offHand = null;

    @Desc("Make other entities ride this entity")
    @ArrayType(min = 1, type = IrisEntity.class)
    private KList<IrisEntity> passengers = new KList<>();

    @Desc("Attribute modifiers for this entity")
    @ArrayType(min = 1, type = IrisAttributeModifier.class)
    private KList<IrisAttributeModifier> attributes = new KList<>();

    @Desc("Loot tables for drops. Only the tables list is honored for entities; mode and multiplier are ignored, and the tables replace vanilla drops entirely.")
    private IrisLootReference loot = new IrisLootReference();

    @Desc("If specified, this entity will be leashed by this entity. I.e. THIS ENTITY Leashed by SPECIFIED. This has no effect on EnderDragons, Withers, Players, or Bats.Non-living entities excluding leashes will not persist as leashholders.")
    private IrisEntity leashHolder = null;

    @Desc("If specified, this entity will spawn with an effect")
    private IrisEffect spawnEffect = null;

    @Desc("Simply moves the entity from below the surface slowly out of the ground as a spawn-in effect")
    private boolean spawnEffectRiseOutOfGround = false;

    @Desc("The main gene for a panda if the entity type is a panda. One of NORMAL, LAZY, WORRIED, PLAYFUL, BROWN, WEAK or AGGRESSIVE.")
    private String pandaMainGene = null;

    @Desc("The hidden gene for a panda if the entity type is a panda. One of NORMAL, LAZY, WORRIED, PLAYFUL, BROWN, WEAK or AGGRESSIVE.")
    private String pandaHiddenGene = null;

    @Desc("The this entity is ageable, set it's baby status")
    private boolean baby = false;

    @Desc("If the entity should never be culled.")
    private boolean keepEntity = false;

    @Desc("The surface type to spawn this mob on")
    private IrisSurface surface = IrisSurface.LAND;

    @RegistryListSpecialEntity
    @Desc("Create a mob from another plugin, such as Mythic Mobs. Should be in the format of a namespace of PluginName:MobName")
    private String specialType = "";

    @Desc("Set to true if you want to apply all of the settings here to the mob, even though an external plugin has already done so.")
    private boolean applySettingsToCustomMobAnyways = false;

    @ArrayType(min = 1, type = IrisCommand.class)
    @Desc("Run raw commands when this entity is spawned. Use {x}, {y}, and {z} for location. /summon pig {x} {y} {z}")
    private KList<IrisCommand> rawCommands = new KList<>();

    public boolean isSpecialType() {
        return specialType != null && !specialType.equals("");
    }

    @Override
    public String getFolderName() {
        return "entities";
    }

    @Override
    public String getTypeName() {
        return "Entity";
    }
}
