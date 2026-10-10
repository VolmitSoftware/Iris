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

/**
 * The public face of Iris's built-in modules: every stage constant, in {@link art.arcane.iris.builtin.IrisStages},
 * and the settings and output types of those stages. {@link art.arcane.iris.core.modules.IrisModuleHost} exposes this package, with its
 * subpackages, to pack modules, so a pack jar can {@code requires(...)} a built-in stage and read its
 * output. Everything here is part of what packs compile against.
 * <p>
 * Keep implementation out of it: stage bodies and anything that uses Iris internals live in the
 * built-in module classes outside this package, which packs cannot see. Settings and output types go in one
 * subpackage per built-in module, named after the module path, such as {@code art.arcane.iris.builtin.deposit}
 * for {@code iris:deposit}.
 * <p>
 * Besides those, it holds what the compiled-in built-ins and the built-ins jar must share, since each sees its own copy
 * of every other class: narrow facades only Iris's own built-ins use, interfaces Iris's worlds implement
 * ({@link art.arcane.iris.builtin.IrisSeed}), and per-world state several of them use
 * ({@link art.arcane.iris.builtin.terrain.LayerBlocks}). Keep them few: each is a promise.
 * <p>
 * Some built-in stages produce Iris's own types, such as the placement settings of {@code iris:prepare}. A
 * pack module can require such a stage to run after it, but cannot read its output: the pack load rejects
 * a pack class that uses a type Iris hides.
 */
package art.arcane.iris.builtin;
