/*
 *  Iris is a World Generator for Minecraft Bukkit Servers
 *  Copyright (c) 2024 Arcane Arts (Volmit Software)
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

package com.volmit.iris.core.link;

import com.volmit.iris.Iris;
import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.core.nms.container.BiomeColor;
import com.volmit.iris.core.service.ExternalDataSVC;
import com.volmit.iris.engine.data.cache.Cache;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.data.B;
import com.volmit.iris.util.data.IrisCustomData;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.reflect.WrappedField;
import io.th0rgal.oraxen.api.OraxenItems;
import io.th0rgal.oraxen.items.ItemBuilder;
import io.th0rgal.oraxen.mechanics.Mechanic;
import io.th0rgal.oraxen.mechanics.MechanicFactory;
import io.th0rgal.oraxen.mechanics.MechanicsManager;
import io.th0rgal.oraxen.mechanics.provided.gameplay.block.BlockMechanic;
import io.th0rgal.oraxen.mechanics.provided.gameplay.block.BlockMechanicFactory;
import io.th0rgal.oraxen.mechanics.provided.gameplay.furniture.FurnitureFactory;
import io.th0rgal.oraxen.mechanics.provided.gameplay.furniture.FurnitureMechanic;
import io.th0rgal.oraxen.mechanics.provided.gameplay.noteblock.NoteBlockMechanicFactory;
import io.th0rgal.oraxen.mechanics.provided.gameplay.stringblock.StringBlockMechanicFactory;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.ItemFrame;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;

import java.util.Map;
import java.util.MissingResourceException;
import java.util.Optional;
import java.util.function.Consumer;

public class OraxenDataProvider extends ExternalDataProvider {

    private static final String FIELD_FACTORIES_MAP = "FACTORIES_BY_MECHANIC_ID";

    private WrappedField<MechanicsManager, Map<String, MechanicFactory>> factories;

    public OraxenDataProvider() {
        super("Oraxen");
    }

    @Override
    public void init() {
        Iris.info("Setting up Oraxen Link...");
        this.factories = new WrappedField<>(MechanicsManager.class, FIELD_FACTORIES_MAP);
        if (this.factories.hasFailed()) {
            Iris.error("Failed to set up Oraxen Link: Unable to fetch MechanicFactoriesMap!");
        }
    }

    @Override
    public BlockData getBlockData(Identifier blockId, KMap<String, String> state) throws MissingResourceException {
        MechanicFactory factory = getFactory(blockId);
        if (factory instanceof NoteBlockMechanicFactory f)
            return f.createNoteBlockData(blockId.key());
        else if (factory instanceof BlockMechanicFactory f) {
            MultipleFacing newBlockData = (MultipleFacing) Bukkit.createBlockData(Material.MUSHROOM_STEM);
            BlockMechanic.setBlockFacing(newBlockData, ((BlockMechanic) f.getMechanic(blockId.key())).getCustomVariation());
            return newBlockData;
        } else if (factory instanceof StringBlockMechanicFactory f) {
            return f.createTripwireData(blockId.key());
        } else if (factory instanceof FurnitureFactory) {
            return new IrisCustomData(B.getAir(), ExternalDataSVC.buildState(blockId, state));
        } else
            throw new MissingResourceException("Failed to find BlockData!", blockId.namespace(), blockId.key());
    }

    @Override
    public ItemStack getItemStack(Identifier itemId, KMap<String, Object> customNbt) throws MissingResourceException {
        Optional<ItemBuilder> opt = OraxenItems.getOptionalItemById(itemId.key());
        return opt.orElseThrow(() -> new MissingResourceException("Failed to find ItemData!", itemId.namespace(), itemId.key())).build();
    }

    @Override
    public void processUpdate(Engine engine, Block block, Identifier blockId) {
        var pair = ExternalDataSVC.parseState(blockId);
        var state = pair.getB();
        blockId = pair.getA();
        Mechanic mechanic = getFactory(blockId).getMechanic(blockId.key());
        if (mechanic instanceof FurnitureMechanic f) {
            float yaw = 0;
            BlockFace face = BlockFace.NORTH;

            long seed = engine.getSeedManager().getSeed() + Cache.key(block.getX(), block.getZ()) + block.getY();
            RNG rng = new RNG(seed);
            if ("true".equals(state.get("randomYaw"))) {
                yaw = rng.f(0, 360);
            } else if (state.containsKey("yaw")) {
                yaw = Float.parseFloat(state.get("yaw"));
            }
            if ("true".equals(state.get("randomFace"))) {
                BlockFace[] faces = BlockFace.values();
                face = faces[rng.i(0, faces.length - 1)];
            } else if (state.containsKey("face")) {
                face = BlockFace.valueOf(state.get("face").toUpperCase());
            }
            if (face == BlockFace.SELF) {
                face = BlockFace.NORTH;
            }
            ItemStack itemStack = OraxenItems.getItemById(f.getItemID()).build();
            Entity entity = f.place(block.getLocation(), itemStack, yaw, face, false);

            Consumer<ItemStack> setter = null;
            if (entity instanceof ItemFrame frame) {
                itemStack = frame.getItem();
                setter = frame::setItem;
            } else if (entity instanceof ItemDisplay display) {
                itemStack = display.getItemStack();
                setter = display::setItemStack;
            }
            if (setter == null || itemStack == null) return;

            BiomeColor type = null;
            try {
                type = BiomeColor.valueOf(state.get("matchBiome").toUpperCase());
            } catch (NullPointerException | IllegalArgumentException ignored) {
            }

            if (type != null) {
                var biomeColor = INMS.get().getBiomeColor(block.getLocation(), type);
                if (biomeColor == null) return;
                var potionColor = Color.fromARGB(biomeColor.getAlpha(), biomeColor.getRed(), biomeColor.getGreen(), biomeColor.getBlue());
                if (itemStack.getItemMeta() instanceof PotionMeta meta) {
                    meta.setColor(potionColor);
                    itemStack.setItemMeta(meta);
                }
            }
            setter.accept(itemStack);
        }
    }

    @Override
    public Identifier[] getBlockTypes() {
        KList<Identifier> names = new KList<>();
        for (String name : OraxenItems.getItemNames()) {
            try {
                Identifier key = new Identifier("oraxen", name);
                if (getBlockData(key) != null)
                    names.add(key);
            } catch (MissingResourceException ignored) {
            }
        }

        return names.toArray(new Identifier[0]);
    }

    @Override
    public Identifier[] getItemTypes() {
        KList<Identifier> names = new KList<>();
        for (String name : OraxenItems.getItemNames()) {
            try {
                Identifier key = new Identifier("oraxen", name);
                if (getItemStack(key) != null)
                    names.add(key);
            } catch (MissingResourceException ignored) {
            }
        }

        return names.toArray(new Identifier[0]);
    }

    @Override
    public boolean isReady() {
        if (super.isReady()) {
            if (factories == null) {
                this.factories = new WrappedField<>(MechanicsManager.class, FIELD_FACTORIES_MAP);
            }
            return super.isReady() && !factories.hasFailed();
        }
        return false;
    }

    @Override
    public boolean isValidProvider(Identifier key, boolean isItem) {
        return key.namespace().equalsIgnoreCase("oraxen");
    }

    private MechanicFactory getFactory(Identifier key) throws MissingResourceException {
        return factories.get().values().stream()
                .filter(i -> i.getItems().contains(key.key()))
                .findFirst()
                .orElseThrow(() -> new MissingResourceException("Failed to find BlockData!", key.namespace(), key.key()));
    }
}
