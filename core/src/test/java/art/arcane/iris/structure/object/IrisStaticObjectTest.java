package art.arcane.iris.structure.object;

import art.arcane.iris.generation.block.IrisBlockData;
import art.arcane.iris.pack.value.IrisPosition;

import art.arcane.volmlib.util.collection.KList;
import com.google.gson.Gson;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class IrisStaticObjectTest {
    @Test
    public void defaultsKeepTheSavedOrientationAndOrigin() {
        IrisStaticObject object = validObject();
        object.validate(-64, 320);
        IrisObjectPlacement placement = object.toPlacement();

        assertEquals(List.of("landmarks/tower"), placement.getPlace());
        assertEquals(ObjectPlaceMode.STRUCTURE_PIECE, placement.getMode());
        assertTrue(placement.isForcePlace());
        assertFalse(placement.getRotation().isEnabled());
        assertNull(placement.getScale());
        assertEquals(new IrisPosition(100, 100, -100), object.getPosition());
    }

    @Test
    public void fixedRotationsPreserveSignedFractionalAnglesForEverySpin() {
        IrisStaticObject object = validObject().setRotation(new IrisStaticObjectRotation()
                .setX(-22.5).setY(90).setZ(-180));
        object.validate(-64, 320);
        IrisObjectRotation rotation = object.toPlacement().getRotation();

        for (int spin : new int[]{0, 17, -222, 359}) {
            assertEquals(Math.toRadians(-22.5), rotation.getXRotation(spin), 0D);
            assertEquals(Math.toRadians(90), rotation.getYRotation(spin), 0D);
            assertEquals(Math.toRadians(-180), rotation.getZRotation(spin), 0D);
        }
        assertFalse(rotation.getXAxis().isForceLock());
    }

    @Test
    public void placementsKeepEditsAndFixedScaleWithoutSharingMutableLists() {
        IrisObjectReplace edit = new IrisObjectReplace().setFind(new KList<>(new IrisBlockData("stone")));
        IrisStaticObject object = validObject()
                .setScale(2.5)
                .setScaleInterpolation(IrisObjectPlacementScaleInterpolator.TRILINEAR)
                .setEdit(new KList<>(edit))
                .setBore(true)
                .setSmartBore(true);
        object.validate(-64, 320);
        IrisObjectPlacement placement = object.toPlacement();

        assertEquals(2.5, placement.getScale().getSize(), 0D);
        assertEquals(IrisObjectPlacementScaleInterpolator.TRILINEAR, placement.getScale().getInterpolation());
        assertTrue(placement.isBore());
        assertTrue(placement.isSmartBore());
        assertEquals(object.getEdit(), placement.getEdit());
        assertNotSame(object.getEdit(), placement.getEdit());
        placement.getEdit().clear();
        assertEquals(1, object.getEdit().size());
    }

    @Test
    public void rejectsMissingFieldsInvalidTransformsAndOutOfWorldOrigins() {
        assertThrows(IllegalArgumentException.class, () -> new IrisStaticObject().validate(-64, 320));
        assertThrows(IllegalArgumentException.class, () -> validObject().setPosition(null).validate(-64, 320));
        assertThrows(IllegalArgumentException.class, () -> validObject().setScale(Double.NaN).validate(-64, 320));
        assertThrows(IllegalArgumentException.class, () -> validObject().setScale(0D).validate(-64, 320));
        assertThrows(IllegalArgumentException.class, () -> validObject().setScale(51D).validate(-64, 320));
        assertThrows(IllegalArgumentException.class, () -> validObject().setRotation(null).validate(-64, 320));
        assertThrows(IllegalArgumentException.class,
                () -> validObject().setRotation(new IrisStaticObjectRotation().setY(-361)).validate(-64, 320));
        assertThrows(IllegalArgumentException.class,
                () -> validObject().setPosition(new IrisPosition(0, 320, 0)).validate(-64, 320));
        assertThrows(IllegalArgumentException.class,
                () -> validObject().setPosition(new IrisPosition(-29_999_985, 0, 0)).validate(-64, 320));
        assertThrows(IllegalArgumentException.class,
                () -> validObject().setEdit(new KList<>(new IrisObjectReplace())).validate(-64, 320));
    }

    @Test
    public void jsonRoundTripKeepsTheLongSeedAndAllFixedSettings() {
        IrisStaticObject source = validObject().setSeed(Long.MAX_VALUE)
                .setRotation(new IrisStaticObjectRotation().setY(-45.5)).setScale(0.5).setBore(true);
        Gson gson = new Gson();
        IrisStaticObject copy = gson.fromJson(gson.toJson(source), IrisStaticObject.class);

        copy.validate(-64, 320);
        assertEquals(source, copy);
        assertEquals(Long.MAX_VALUE, copy.getSeed());
    }

    private static IrisStaticObject validObject() {
        return new IrisStaticObject().setObject("landmarks/tower").setPosition(new IrisPosition(100, 100, -100));
    }
}
