/*
 * Iris is a World Generator for Minecraft Bukkit Servers
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

package art.arcane.iris.spi;

import art.arcane.iris.testsupport.PlatformLeakGuard;
import org.junit.ClassRule;
import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class IrisServicesTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Test
    public void removeThenReregisterRebindsService() {
        Sample first = new Sample();
        Sample second = new Sample();

        IrisServices.register(Sample.class, first);
        assertSame(first, IrisServices.get(Sample.class));

        IrisServices.remove(Sample.class);
        assertNull(IrisServices.getOrNull(Sample.class));

        IrisServices.register(Sample.class, second);
        assertSame(second, IrisServices.get(Sample.class));

        IrisServices.remove(Sample.class);
        assertNull(IrisServices.getOrNull(Sample.class));
    }

    @Test
    public void removeMissingServiceIsNoOp() {
        IrisServices.remove(Sample.class);
        assertNull(IrisServices.getOrNull(Sample.class));
    }

    private static final class Sample {
    }
}
