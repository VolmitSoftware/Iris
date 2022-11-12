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

package com.volmit.iris.util.particle;

import com.volmit.iris.Iris;
import org.bukkit.Color;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.MaterialData;

/**
 * @author MrMicky
 */
@SuppressWarnings("deprecation")
public enum ParticleType {

    // 1.7+
    EXPLOSION_NORMAL("explode", "poof"),
    EXPLOSION_LARGE("largeexplode", "explosion"),
    EXPLOSION_HUGE("hugeexplosion", "explosion_emitter"),
    FIREWORKS_SPARK("fireworksSpark", "firework"),
    WATER_BUBBLE("bubble", "bubble"),
    WATER_SPLASH("splash", "splash"),
    WATER_WAKE("wake", "fishing"),
    SUSPENDED("suspended", "underwater"),
    SUSPENDED_DEPTH("depthsuspend", "underwater"),
    CRIT("crit", "crit"),
    CRIT_MAGIC("magicCrit", "enchanted_hit"),
    SMOKE_NORMAL("smoke", "smoke"),
    SMOKE_LARGE("largesmoke", "large_smoke"),
    SPELL("spell", "effect"),
    SPELL_INSTANT("instantSpell", "instant_effect"),
    SPELL_MOB("mobSpell", "entity_effect"),
    SPELL_MOB_AMBIENT("mobSpellAmbient", "ambient_entity_effect"),
    SPELL_WITCH("witchMagic", "witch"),
    DRIP_WATER("dripWater", "dripping_water"),
    DRIP_LAVA("dripLava", "dripping_lava"),
    VILLAGER_ANGRY("angryVillager", "angry_villager"),
    VILLAGER_HAPPY("happyVillager", "happy_villager"),
    TOWN_AURA("townaura", "mycelium"),
    NOTE("note", "note"),
    PORTAL("portal", "portal"),
    ENCHANTMENT_TABLE("enchantmenttable", "enchant"),
    FLAME("flame", "flame"),
    LAVA("lava", "lava"),
    // FOOTSTEP("footstep", null),
    CLOUD("cloud", "cloud"),
    REDSTONE("reddust", "dust"),
    SNOWBALL("snowballpoof", "item_snowball"),
    SNOW_SHOVEL("snowshovel", "item_snowball"),
    SLIME("slime", "item_slime"),
    HEART("heart", "heart"),
    ITEM_CRACK("iconcrack", "item"),
    BLOCK_CRACK("blockcrack", "block"),
    BLOCK_DUST("blockdust", "block"),

    // 1.8+
    BARRIER("barrier", "barrier", 8),
    WATER_DROP("droplet", "rain", 8),
    MOB_APPEARANCE("mobappearance", "elder_guardian", 8),
    // ITEM_TAKE("take", null, 8),

    // 1.9+
    DRAGON_BREATH("dragonbreath", "dragon_breath", 9),
    END_ROD("endRod", "end_rod", 9),
    DAMAGE_INDICATOR("damageIndicator", "damage_indicator", 9),
    SWEEP_ATTACK("sweepAttack", "sweep_attack", 9),

    // 1.10+
    FALLING_DUST("fallingdust", "falling_dust", 10),

    // 1.11+
    TOTEM("totem", "totem_of_undying", 11),
    SPIT("spit", "spit", 11),

    // 1.13+
    SQUID_INK(13),
    BUBBLE_POP(13),
    CURRENT_DOWN(13),
    BUBBLE_COLUMN_UP(13),
    NAUTILUS(13),
    DOLPHIN(13),

    // 1.14+
    SNEEZE(14),
    CAMPFIRE_COSY_SMOKE(14),
    CAMPFIRE_SIGNAL_SMOKE(14),
    COMPOSTER(14),
    FLASH(14),
    FALLING_LAVA(14),
    LANDING_LAVA(14),
    FALLING_WATER(14),

    // 1.15+
    DRIPPING_HONEY(15),
    FALLING_HONEY(15),
    LANDING_HONEY(15),
    FALLING_NECTAR(15);

    private static final int SERVER_VERSION_ID;

    static {
        String ver = FastReflection.VERSION;
        SERVER_VERSION_ID = ver.charAt(4) == '_' ? Character.getNumericValue(ver.charAt(3)) : Integer.parseInt(ver.substring(3, 5));
    }

    private final String legacyName;
    private final String name;
    private final int minimumVersion;

    // 1.7 particles
    ParticleType(String legacyName, String name) {
        this(legacyName, name, -1);
    }

    // 1.13+ particles
    ParticleType(int minimumVersion) {
        this.legacyName = null;
        this.name = name().toLowerCase();
        this.minimumVersion = minimumVersion;
    }

    // 1.8-1.12 particles
    ParticleType(String legacyName, String name, int minimumVersion) {
        this.legacyName = legacyName;
        this.name = name;
        this.minimumVersion = minimumVersion;
    }

    public static ParticleType getParticle(String particleName) {
        try {
            return ParticleType.valueOf(particleName.toUpperCase());
        } catch (IllegalArgumentException e) {
            Iris.reportError(e);
            for (ParticleType particle : values()) {
                if (particle.getName().equalsIgnoreCase(particleName)) {
                    return particle;
                }

                if (particle.hasLegacyName() && particle.getLegacyName().equalsIgnoreCase(particleName)) {
                    return particle;
                }
            }
        }
        return null;
    }

    public boolean hasLegacyName() {
        return legacyName != null;
    }

    public String getLegacyName() {
        if (!hasLegacyName()) {
            throw new IllegalStateException("Particle " + name() + " don't have legacy name");
        }
        return legacyName;
    }

    public String getName() {
        return name;
    }

    public boolean isSupported() {
        return minimumVersion <= 0 || SERVER_VERSION_ID >= minimumVersion;
    }

    public Class<?> getDataType() {
        return switch (this) {
            case ITEM_CRACK -> ItemStack.class;
            case BLOCK_CRACK, BLOCK_DUST, FALLING_DUST ->
                //noinspection deprecation
                    MaterialData.class;
            case REDSTONE -> Color.class;
            default -> Void.class;
        };
    }
}
