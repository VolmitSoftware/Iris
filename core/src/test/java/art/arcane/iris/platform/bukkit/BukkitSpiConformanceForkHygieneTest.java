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

package art.arcane.iris.platform.bukkit;

import org.junit.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * org.bukkit.Registry resolves its static fields through a live RegistryAccess, which no unit test binds.
 * A failed static initialiser is permanent and JVM-wide, so a probe that touches Registry without one
 * breaks every later test in the same Gradle fork - a try/catch around the probe cannot undo it.
 * Replaying the conformance suite against fork-local copies of the Bukkit classes makes that leak
 * observable without poisoning the fork this test runs in.
 */
public class BukkitSpiConformanceForkHygieneTest {
    private static final String CONFORMANCE = "art.arcane.iris.platform.bukkit.BukkitSpiConformanceTest";
    private static final String REGISTRY = "org.bukkit.Registry";

    @Test
    public void conformanceSuiteLeavesBukkitRegistryInitialisable() throws Exception {
        ForkLocalClassLoader isolated = new ForkLocalClassLoader(getClass().getClassLoader());
        Result result = JUnitCore.runClasses(Class.forName(CONFORMANCE, true, isolated));
        assertTrue("the conformance suite did not run in isolation", result.getRunCount() > 0);
        assertEquals(result.getFailures().toString(), 0, result.getFailureCount());

        Throwable initialisation = null;

        try {
            Class.forName(REGISTRY, true, isolated);
        } catch (Throwable thrown) {
            initialisation = thrown;
        }

        assertFalse(REGISTRY + " was left permanently un-initialisable by the conformance suite: " + initialisation,
                initialisation instanceof NoClassDefFoundError);
    }

    /** Child-first for the Bukkit and Iris classes under test, parent for JUnit, Mockito and the JDK. */
    private static final class ForkLocalClassLoader extends ClassLoader {
        private static final String[] FORK_LOCAL = {
                "org.bukkit.",
                "io.papermc.",
                "com.destroystokyo.",
                "art.arcane.iris.",
                "art.arcane.volmlib."
        };

        static {
            registerAsParallelCapable();
        }

        ForkLocalClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);

                if (loaded == null) {
                    loaded = forkLocal(name) ? define(name) : getParent().loadClass(name);
                }

                if (resolve) {
                    resolveClass(loaded);
                }

                return loaded;
            }
        }

        private Class<?> define(String name) throws ClassNotFoundException {
            try (InputStream source = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                if (source == null) {
                    throw new ClassNotFoundException(name);
                }

                byte[] bytes = source.readAllBytes();
                return defineClass(name, bytes, 0, bytes.length);
            } catch (IOException unreadable) {
                throw new ClassNotFoundException(name, unreadable);
            }
        }

        private static boolean forkLocal(String name) {
            for (String prefix : FORK_LOCAL) {
                if (name.startsWith(prefix)) {
                    return true;
                }
            }

            return false;
        }
    }
}
