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

package art.arcane.iris.engine.framework;

import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.core.modules.ModuleServices;
import art.arcane.iris.platform.bukkit.BukkitAmbientEffects;
import art.arcane.iris.platform.bukkit.BukkitWorldBinding;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.util.common.scheduling.J;
import art.arcane.volmlib.util.math.M;
import com.volmit.world.BlockPosition;
import lombok.Data;
import org.bukkit.Location;
import org.bukkit.entity.Player;

@Data
public class EnginePlayer {
    private final Engine engine;
    private final Player player;
    private BlockPosition sampledPosition;
    private Location lastLocation;
    private long lastSample;

    public EnginePlayer(Engine engine, Player player) {
        this.engine = engine;
        this.player = player;
        lastLocation = null;
        lastSample = -1;
    }

    public void tick() {
        J.runEntity(player, this::tickOnEntity);
    }

    private void tickOnEntity() {
        if (sample() || !IrisSettings.get().getWorld().isEffectSystem()) {
            return;
        }

        if (sampledPosition == null) {
            return;
        }

        try {
            BukkitAmbientEffects.play(player, engine, ModuleServices.ambientEffects(engine, sampledPosition));
        } catch (Throwable e) {
            IrisLogging.reportError(e);
        }
    }

    public long ticksSinceLastSample() {
        return M.ms() - lastSample;
    }

    public boolean sample() {
        Location current = player.getLocation().clone();
        if (current.getWorld() != BukkitWorldBinding.world(engine.getWorld())) {
            return true;
        }
        try {
            boolean sampled = lastLocation != null;
            double distanceSquared = sampled ? current.distanceSquared(lastLocation) : 0D;
            if (needsSample(sampled, ticksSinceLastSample(), distanceSquared)) {
                lastLocation = current;
                lastSample = M.ms();
                sampledPosition = new BlockPosition(current.getBlockX(), current.getBlockY() - engine.getWorld().minHeight(), current.getBlockZ());
            }
            return false;
        } catch (Throwable e) {
            IrisLogging.reportError(e);

        }
        return true;
    }

    static boolean needsSample(boolean sampled, long elapsedMillis, double distanceSquared) {
        return !sampled || elapsedMillis > 55L && distanceSquared > 81D;
    }
}
