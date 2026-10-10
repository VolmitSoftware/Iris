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

package art.arcane.iris.builtin.spawn;

import art.arcane.iris.builtin.loot.ItemAttributeModifier;
import art.arcane.iris.builtin.loot.LootEntry;
import art.arcane.iris.builtin.loot.LootSettings;
import com.volmit.config.Desc;
import com.volmit.config.MinItems;
import com.volmit.config.Ref;
import com.volmit.config.Reference;
import com.volmit.config.Required;

import java.util.Collections;
import java.util.List;

/** An entity of the pack, one file of {@code entities/}: what spawners and markers spawn, with its equipment, attributes, passengers and loot. */
@Desc("Represents an iris entity.")
public final class EntityDefinition {
    @Required
    @Ref(Reference.ENTITY)
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
    private LootEntry helmet = null;

    @Desc("Entity chestplate equipment")
    private LootEntry chestplate = null;

    @Desc("Entity boots equipment")
    private LootEntry boots = null;

    @Desc("Entity leggings equipment")
    private LootEntry leggings = null;

    @Desc("Entity main hand equipment")
    private LootEntry mainHand = null;

    @Desc("Entity off hand equipment")
    private LootEntry offHand = null;

    @MinItems(1)
    @Desc("Make other entities ride this entity")
    private List<EntityDefinition> passengers = List.of();

    @MinItems(1)
    @Desc("Attribute modifiers for this entity")
    private List<ItemAttributeModifier> attributes = List.of();

    @Desc("Loot tables for drops. Only the tables list is honored for entities; mode and multiplier are ignored, and the tables replace vanilla drops entirely.")
    private LootSettings loot = new LootSettings();

    @Desc("If specified, this entity will be leashed by this entity. I.e. THIS ENTITY Leashed by SPECIFIED. This has no effect on EnderDragons, Withers, Players, or Bats.Non-living entities excluding leashes will not persist as leashholders.")
    private EntityDefinition leashHolder = null;

    @Desc("If specified, this entity will spawn with an effect")
    private AmbientEffect spawnEffect = null;

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
    private SpawnSurface surface = SpawnSurface.LAND;

    @Ref(Reference.SPECIAL_ENTITY)
    @Desc("Create a mob from another plugin, such as Mythic Mobs. Should be in the format of a namespace of PluginName:MobName")
    private String specialType = "";

    @Desc("Set to true if you want to apply all of the settings here to the mob, even though an external plugin has already done so.")
    private boolean applySettingsToCustomMobAnyways = false;

    @MinItems(1)
    @Desc("Run raw commands when this entity is spawned. Use {x}, {y}, and {z} for location. /summon pig {x} {y} {z}")
    private List<SpawnCommand> rawCommands = List.of();

    public EntityDefinition() {
    }

    public String type() {
        return type;
    }

    public String reason() {
        return reason;
    }

    public String customName() {
        return customName;
    }

    public boolean customNameVisible() {
        return customNameVisible;
    }

    public boolean aware() {
        return aware;
    }

    public boolean ai() {
        return ai;
    }

    public boolean glowing() {
        return glowing;
    }

    public boolean gravity() {
        return gravity;
    }

    public boolean invulnerable() {
        return invulnerable;
    }

    public boolean silent() {
        return silent;
    }

    public boolean pickupItems() {
        return pickupItems;
    }

    public boolean removable() {
        return removable;
    }

    public LootEntry helmet() {
        return helmet;
    }

    public LootEntry chestplate() {
        return chestplate;
    }

    public LootEntry boots() {
        return boots;
    }

    public LootEntry leggings() {
        return leggings;
    }

    public LootEntry mainHand() {
        return mainHand;
    }

    public LootEntry offHand() {
        return offHand;
    }

    public List<EntityDefinition> passengers() {
        return passengers == null ? List.of() : Collections.unmodifiableList(passengers);
    }

    public List<ItemAttributeModifier> attributes() {
        return attributes == null ? List.of() : Collections.unmodifiableList(attributes);
    }

    /** The loot the entity drops when it dies; only its tables count. */
    public LootSettings loot() {
        return loot == null ? new LootSettings() : loot;
    }

    public EntityDefinition leashHolder() {
        return leashHolder;
    }

    public AmbientEffect spawnEffect() {
        return spawnEffect;
    }

    public boolean spawnEffectRiseOutOfGround() {
        return spawnEffectRiseOutOfGround;
    }

    public String pandaMainGene() {
        return pandaMainGene;
    }

    public String pandaHiddenGene() {
        return pandaHiddenGene;
    }

    public boolean baby() {
        return baby;
    }

    public boolean keepEntity() {
        return keepEntity;
    }

    public SpawnSurface surface() {
        return surface == null ? SpawnSurface.LAND : surface;
    }

    public String specialType() {
        return specialType;
    }

    /** Whether the entity is a mob of another plugin. */
    public boolean isSpecialType() {
        return specialType != null && !specialType.isEmpty();
    }

    public boolean applySettingsToCustomMobAnyways() {
        return applySettingsToCustomMobAnyways;
    }

    public List<SpawnCommand> rawCommands() {
        return rawCommands == null ? List.of() : Collections.unmodifiableList(rawCommands);
    }
}
