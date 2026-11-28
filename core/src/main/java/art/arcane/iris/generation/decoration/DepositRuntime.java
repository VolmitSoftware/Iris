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

package art.arcane.iris.generation.decoration;

import art.arcane.iris.generation.runtime.PackRuntime;
import art.arcane.iris.generation.runtime.SeedTable;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.volmlib.util.collection.KList;

/**
 * What one engine derives from an {@link IrisDepositGenerator}: the clump variants of the engine's deposit seed, at
 * the configured size and at each scaled size the deposit modifier asks for.
 */
public final class DepositRuntime {
    private static final int SCALED_SIZE_CAPACITY = 64;

    private final IrisDepositGenerator deposit;
    private final PackRuntime runtime;
    private final long depositSeed;
    private final SeedTable<KList<IrisObject>> scaledClumps;
    private volatile KList<IrisObject> clumps;

    public DepositRuntime(IrisDepositGenerator deposit, PackRuntime runtime) {
        this.deposit = deposit;
        this.runtime = runtime;
        this.depositSeed = runtime.engine().getSeedManager().getDeposit();
        this.scaledClumps = new SeedTable<>(SCALED_SIZE_CAPACITY, sizes -> deposit.createScaledClumps(
                depositSeed, minSize(sizes), maxSize(sizes), runtime.data()));
    }

    public KList<IrisObject> clumps() {
        KList<IrisObject> built = clumps;
        if (built != null) {
            return built;
        }
        synchronized (this) {
            if (clumps == null) {
                clumps = deposit.createClumps(depositSeed, runtime.data());
            }
            return clumps;
        }
    }

    public KList<IrisObject> scaledClumps(int minSize, int maxSize) {
        return scaledClumps.get(sizeKey(minSize, maxSize));
    }

    private static long sizeKey(int minSize, int maxSize) {
        return ((long) minSize << 32) | (maxSize & 0xffffffffL);
    }

    private static int minSize(long sizeKey) {
        return (int) (sizeKey >>> 32);
    }

    private static int maxSize(long sizeKey) {
        return (int) sizeKey;
    }
}
