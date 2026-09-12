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

package art.arcane.iris.generation.runtime;

import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.volmlib.util.math.RollingSequence;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertSame;

public class EngineComponentTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @After
    public void removeCleanup() {
        IrisServices.remove(EngineComponentCleanup.class);
    }

    @Test
    public void closeDelegatesToRegisteredPlatformCleanup() {
        AtomicReference<EngineComponent> released = new AtomicReference<>();
        IrisServices.register(EngineComponentCleanup.class, (EngineComponentCleanup) released::set);
        EngineComponent component = new TestComponent();

        component.close();

        assertSame(component, released.get());
    }

    @Test
    public void closeWithoutPlatformCleanupIsSafe() {
        new TestComponent().close();
    }

    private static final class TestComponent implements EngineComponent {
        @Override
        public Engine getEngine() {
            return null;
        }

        @Override
        public RollingSequence getMetrics() {
            return null;
        }

        @Override
        public String getName() {
            return "test";
        }
    }
}
