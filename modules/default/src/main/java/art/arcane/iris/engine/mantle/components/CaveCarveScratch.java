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

package art.arcane.iris.engine.mantle.components;

import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.matter.MatterSlice;

final class CaveCarveScratch {
    final int[] columnMaxY = new int[256];
    final int[] fluidMaxY = new int[256];
    final int[] surfaceBreakFloorY = new int[256];
    final boolean[] surfaceBreakColumn = new boolean[256];
    final boolean[] surfaceCeilingColumn = new boolean[256];
    final double[] columnThreshold = new double[256];
    final double[] passThreshold = new double[256];
    final double[] fullWeights = new double[256];
    final double[] clampedColumnWeights = new double[256];
    final int[] activeColumnIndices = new int[256];
    final int[] activeColumnTopY = new int[256];
    final int[] planeColumnIndices = new int[256];
    final double[] planeThresholdLimit = new double[256];
    final boolean[] planeCarve = new boolean[256];
    final double[] adaptivePlaneDensity = new double[81];
    final double[] adaptivePlanePrediction = new double[256];
    final double[] adaptivePlaneAmbiguity = new double[256];
    final int[] adaptivePlaneSampleBounds = new int[4];
    final int[] adaptiveCellX = new int[256];
    final int[] adaptiveCellZ = new int[256];
    final int[] adaptiveRow0 = new int[256];
    final int[] adaptiveRow1 = new int[256];
    final double[] adaptiveTx = new double[256];
    final double[] adaptiveTz = new double[256];
    final boolean[] warpCacheSet = new boolean[256];
    final int[] warpCacheX = new int[256];
    final int[] warpCacheY = new int[256];
    final int[] warpCacheZ = new int[256];
    final double[] warpCacheA = new double[256];
    final double[] warpCacheB = new double[256];
    final int[] tileIndices = new int[4];
    final int[] tileLocalX = new int[4];
    final int[] tileLocalZ = new int[4];
    final int[] tileTopY = new int[4];
    CaveFieldModuleState[] activeModules = new CaveFieldModuleState[0];
    double[] activeModuleRemainingMin = new double[0];
    double[] activeModuleRemainingMax = new double[0];
    int activeModulesY = Integer.MIN_VALUE;
    int activeModuleCount;
    double[] verticalEdgeFade = new double[0];
    double[] surfaceClosureThreshold = new double[0];
    MatterCavern[] matterByY = new MatterCavern[0];
    Matter[] sectionMatter = new Matter[0];
    MatterSlice<?>[] sectionSlices = new MatterSlice<?>[0];
    int adaptiveGeometryStep = -1;
    int adaptiveGeometryAxisCells = -1;
    boolean fullWeightsInitialized;
}
