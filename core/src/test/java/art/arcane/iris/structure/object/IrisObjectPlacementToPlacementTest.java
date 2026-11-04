package art.arcane.iris.structure.object;

import art.arcane.iris.generation.cave.CarvingMode;
import art.arcane.iris.generation.cave.IrisCaveAnchorMode;
import art.arcane.iris.generation.decoration.IrisStiltSettings;
import art.arcane.iris.generation.decoration.tree.IrisTree;
import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.noise.IrisNoiseGenerator;
import art.arcane.iris.generation.noise.IrisStyledRange;
import art.arcane.iris.generation.noise.NoiseStyle;
import art.arcane.iris.generation.terrain.IrisSlopeClip;

import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class IrisObjectPlacementToPlacementTest {
    @Test
    public void toPlacement_preservesInheritedPlacementMetadata() {
        IrisObjectPlacement source = new IrisObjectPlacement();
        IrisObjectRotation rotation = new IrisObjectRotation();
        IrisObjectLimit clamp = new IrisObjectLimit();
        IrisStyledRange densityStyle = new IrisStyledRange();
        densityStyle.setMin(7D);
        densityStyle.setMax(7D);
        IrisStiltSettings stiltSettings = new IrisStiltSettings();
        KList<IrisObjectMarker> markers = new KList<>();
        IrisNoiseGenerator heightmap = new IrisNoiseGenerator(false);
        IrisGeneratorStyle warp = NoiseStyle.SIMPLEX.style();
        KList<IrisObjectReplace> edit = new KList<>();
        IrisObjectTranslate translate = new IrisObjectTranslate();
        IrisObjectScale scale = new IrisObjectScale();
        KList<IrisObjectLoot> loot = new KList<>();
        KList<IrisObjectVanillaLoot> vanillaLoot = new KList<>();
        KList<IrisTree> trees = new KList<>();
        KList<String> allowedCollisions = new KList<>();
        KList<String> forbiddenCollisions = new KList<>();
        IrisSlopeClip slopeCondition = new IrisSlopeClip();

        markers.add(new IrisObjectMarker());
        edit.add(new IrisObjectReplace());
        loot.add(new IrisObjectLoot());
        vanillaLoot.add(new IrisObjectVanillaLoot());
        trees.add(new IrisTree());
        allowedCollisions.add("objects/allowed");
        forbiddenCollisions.add("objects/forbidden");

        source.setRotation(rotation);
        source.setClamp(clamp);
        source.setSnow(0.4D);
        source.setDolphinTarget(true);
        source.setSlopeCondition(slopeCondition);
        source.setRotateTowardsSlope(true);
        source.setChance(0.25D);
        source.setDensity(7);
        source.setDensityStyle(densityStyle);
        source.setStiltSettings(stiltSettings);
        source.setBoreExtendMaxY(8);
        source.setMarkers(markers);
        source.setBoreExtendMinY(3);
        source.setUnderwater(true);
        source.setCarvingSupport(CarvingMode.ANYWHERE);
        source.setSurfaceSupportBuffer(3);
        source.setSurfaceSupportDepth(4);
        source.setRequireSurfaceSupport(false);
        source.setCaveAnchorMode(IrisCaveAnchorMode.CEILING);
        source.setHeightmap(heightmap);
        source.setSmartBore(true);
        source.setWaterloggable(true);
        source.setOnwater(true);
        source.setMeld(true);
        source.setFromBottom(true);
        source.setBottom(true);
        source.setBore(true);
        source.setWarp(warp);
        source.setMode(ObjectPlaceMode.FAST_MIN_STILT);
        source.setEdit(edit);
        source.setTranslate(translate);
        source.setScale(scale);
        source.setLoot(loot);
        source.setVanillaLoot(vanillaLoot);
        source.setOverrideGlobalLoot(true);
        source.setTrees(trees);
        source.setAllowedCollisions(allowedCollisions);
        source.setForbiddenCollisions(forbiddenCollisions);
        source.setForcePlace(true);

        IrisObjectPlacement copy = source.toPlacement("objects/replaced");

        assertEquals(1, copy.getPlace().size());
        assertEquals("objects/replaced", copy.getPlace().get(0));
        assertSame(rotation, copy.getRotation());
        assertSame(clamp, copy.getClamp());
        assertEquals(0.4D, copy.getSnow(), 0.0D);
        assertTrue(copy.isDolphinTarget());
        assertSame(slopeCondition, copy.getSlopeCondition());
        assertTrue(copy.isRotateTowardsSlope());
        assertEquals(0.25D, copy.getChance(), 0.0D);
        assertEquals(7, copy.getDensity());
        assertSame(densityStyle, copy.getDensityStyle());
        assertSame(stiltSettings, copy.getStiltSettings());
        assertEquals(8, copy.getBoreExtendMaxY());
        assertSame(markers, copy.getMarkers());
        assertEquals(3, copy.getBoreExtendMinY());
        assertTrue(copy.isUnderwater());
        assertEquals(CarvingMode.ANYWHERE, copy.getCarvingSupport());
        assertEquals(3, copy.getSurfaceSupportBuffer());
        assertEquals(4, copy.getSurfaceSupportDepth());
        assertFalse(copy.isRequireSurfaceSupport());
        assertEquals(IrisCaveAnchorMode.CEILING, copy.getCaveAnchorMode());
        assertSame(heightmap, copy.getHeightmap());
        assertTrue(copy.isSmartBore());
        assertTrue(copy.isWaterloggable());
        assertTrue(copy.isOnwater());
        assertTrue(copy.isMeld());
        assertTrue(copy.isFromBottom());
        assertTrue(copy.isBottom());
        assertTrue(copy.isBore());
        assertSame(warp, copy.getWarp());
        assertEquals(ObjectPlaceMode.FAST_MIN_STILT, copy.getMode());
        assertSame(edit, copy.getEdit());
        assertSame(translate, copy.getTranslate());
        assertSame(scale, copy.getScale());
        assertSame(loot, copy.getLoot());
        assertSame(vanillaLoot, copy.getVanillaLoot());
        assertTrue(copy.isOverrideGlobalLoot());
        assertSame(trees, copy.getTrees());
        assertSame(allowedCollisions, copy.getAllowedCollisions());
        assertSame(forbiddenCollisions, copy.getForbiddenCollisions());
        assertTrue(copy.isForcePlace());
    }
}
