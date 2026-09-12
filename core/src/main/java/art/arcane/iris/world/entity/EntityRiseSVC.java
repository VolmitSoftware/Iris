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

package art.arcane.iris.world.entity;

import art.arcane.iris.platform.bukkit.plugin.IrisService;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class EntityRiseSVC implements IrisService {
    private static final NamespacedKey RISE_STAMP = new NamespacedKey("iris", "rise_restore");

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
    }

    public static void stamp(Entity entity, boolean invulnerable, boolean ai, boolean collidable) {
        byte flags = 0;
        if (invulnerable) {
            flags |= 1;
        }
        if (ai) {
            flags |= 2;
        }
        if (collidable) {
            flags |= 4;
        }
        entity.getPersistentDataContainer().set(RISE_STAMP, PersistentDataType.BYTE, flags);
    }

    public static void clearStamp(Entity entity) {
        entity.getPersistentDataContainer().remove(RISE_STAMP);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(EntitiesLoadEvent e) {
        for (Entity entity : e.getEntities()) {
            restoreStranded(entity);
        }
    }

    private static void restoreStranded(Entity entity) {
        PersistentDataContainer container = entity.getPersistentDataContainer();
        Byte flags = container.get(RISE_STAMP, PersistentDataType.BYTE);
        if (flags == null) {
            return;
        }

        container.remove(RISE_STAMP);
        entity.setInvulnerable((flags & 1) != 0);
        if (entity instanceof LivingEntity living) {
            living.setAI((flags & 2) != 0);
            living.setCollidable((flags & 4) != 0);
            living.setNoDamageTicks(0);
        }
    }
}
