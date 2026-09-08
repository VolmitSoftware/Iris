package art.arcane.iris.engine.object;

import art.arcane.iris.core.compat.CompatAction;
import art.arcane.iris.core.compat.CompatFinding;
import art.arcane.iris.core.compat.CompatRegistry;
import art.arcane.iris.core.compat.CompatStatus;
import art.arcane.iris.core.compat.ContentGate;
import art.arcane.iris.core.compat.PackCompatReport;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.object.annotations.ArrayType;
import art.arcane.iris.engine.object.annotations.Desc;
import art.arcane.iris.engine.object.annotations.MaxNumber;
import art.arcane.iris.engine.object.annotations.MinNumber;
import art.arcane.iris.engine.object.annotations.RegistryListResource;
import art.arcane.iris.engine.object.annotations.Required;
import art.arcane.volmlib.util.collection.KList;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@Accessors(chain = true)
@Desc("One object placed at a fixed world position, with transformations and edits applied to a copy")
public class IrisStaticObject {
    public static final int MAXIMUM_HORIZONTAL_POSITION = 29_999_984;
    public static final double MINIMUM_SCALE = 0.01D;
    public static final double MAXIMUM_SCALE = 50D;
    /** Subject type of every version-content finding a static object records. */
    public static final String COMPAT_SUBJECT = "static object";

    @Required
    @RegistryListResource(IrisObject.class)
    @Desc("Object load key under objects/, without the .iob extension")
    private String object;
    @Required
    @Desc("Absolute world X, Y and Z for the object's saved origin. Rotation and scaling keep this origin fixed.")
    private IrisPosition position;
    @Desc("Fixed rotation around the saved object origin, in degrees. All axes default to zero.")
    private IrisStaticObjectRotation rotation = new IrisStaticObjectRotation();
    @MinNumber(MINIMUM_SCALE)
    @MaxNumber(MAXIMUM_SCALE)
    @Desc("Explicit fixed size multiplier. When omitted, the dimension's allObjectScaleFactor applies. An explicit 1 preserves the saved size.")
    private Double scale;
    @Desc("Interpolation used when enlarging the object. NONE preserves blocky shapes.")
    private IrisObjectPlacementScaleInterpolator scaleInterpolation = IrisObjectPlacementScaleInterpolator.NONE;
    @ArrayType(type = IrisObjectReplace.class)
    @Desc("Material and block-state replacements applied before rotation, without changing the source object")
    private KList<IrisObjectReplace> edit = new KList<>();
    @Desc("Replace blocks inside the object's transformed bounding box with air before placing its blocks")
    private boolean bore = false;
    @Desc("Fill enclosed rooms and pockets with air before placement, preserving their empty interiors")
    private boolean smartBore = false;
    @Desc("Fixed seed for block replacement palettes and probabilities, independent of the world seed and chunk generation order.")
    private long seed = 0L;

    public void validate(int minY, int maxY) {
        if (object == null || object.isBlank()) {
            throw new IllegalArgumentException("object must be a nonblank object load key.");
        }
        if (position == null) {
            throw new IllegalArgumentException("position is required.");
        }
        if (position.getX() < -MAXIMUM_HORIZONTAL_POSITION || position.getX() > MAXIMUM_HORIZONTAL_POSITION
                || position.getZ() < -MAXIMUM_HORIZONTAL_POSITION || position.getZ() > MAXIMUM_HORIZONTAL_POSITION) {
            throw new IllegalArgumentException("position.x and position.z must be between "
                    + -MAXIMUM_HORIZONTAL_POSITION + " and " + MAXIMUM_HORIZONTAL_POSITION + ".");
        }
        if (position.getY() < minY || position.getY() >= maxY) {
            throw new IllegalArgumentException("position.y must be within the dimension's world height "
                    + minY + " <= y < " + maxY + ".");
        }
        if (rotation == null) {
            throw new IllegalArgumentException("rotation must be an object.");
        }
        rotation.validate();
        if (scale != null) {
            IrisObjectScale.requireValidFactor(scale, "scale");
        }
        if (scaleInterpolation == null) {
            throw new IllegalArgumentException("scaleInterpolation must be a supported interpolation mode.");
        }
        if (edit == null || edit.contains(null)) {
            throw new IllegalArgumentException("edit must be an array of block replacements.");
        }
        for (int index = 0; index < edit.size(); index++) {
            validateEdit(edit.get(index), index);
        }
    }

    /**
     * Version-content gate verdict for this entry on the running server, mirroring a one-object placement: an
     * {@code edit[].replace} palette option the server does not have excludes the entry, and a palette key of the
     * object that neither resolves (dimension {@code blockFallbacks} count) nor is rewritten by a matching chance-1
     * edit rule drops it. Either way the static layer leaves the entry out and the pack report names it as a static
     * object by its object key. Not cached: the dimension compiles its static layer once per snapshot and validation
     * evaluates once on a detached loader; the report deduplicates findings.
     *
     * @param dimensionKey load key of the owning dimension, for the finding detail
     * @param index        position of this entry in the dimension's {@code staticObjects}
     */
    public CompatStatus evaluateCompat(IrisData data, String dimensionKey, int index) {
        if (data == null || object == null || object.isBlank()) {
            return CompatStatus.OK;
        }

        ContentGate gate = data.getContentGate();
        PackCompatReport report = data.getCompatReport();

        if (gate == null || !gate.ready()) {
            // Registry not bound or not ready: UNKNOWN is never MISSING, so nothing is gated.
            if (report != null) {
                report.markIncomplete("registry not ready while evaluating static objects");
            }
            return CompatStatus.OK;
        }

        String detail = dimensionKey + " staticObjects[" + index + "]";
        KList<CompatFinding> reasons = new KList<>();
        boolean excluded = IrisObjectCompat.evaluateEditPalettes(
                edit, data, gate, COMPAT_SUBJECT, object, detail + " ", reasons) != null;

        if (!excluded) {
            // One finding per missing key, so the operator sees every fallback the object needs at once.
            for (String key : IrisObjectCompat.unplaceableKeys(
                    object, edit, data, gate, COMPAT_SUBJECT, object, detail, reasons)) {
                reasons.add(new CompatFinding(CompatRegistry.BLOCK, key, CompatAction.DROPPED,
                        COMPAT_SUBJECT, object, detail));
                excluded = true;
            }
        }

        IrisObjectCompat.record(report, reasons);
        return excluded ? CompatStatus.excludedBy(reasons) : new CompatStatus(false, reasons);
    }

    public IrisObjectPlacement toPlacement() {
        return new IrisObjectPlacement()
                .setPlace(new KList<>(object))
                .setMode(ObjectPlaceMode.STRUCTURE_PIECE)
                .setForcePlace(true)
                .setRotation(rotation.toRotation())
                .setScale(scale == null ? null
                        : new IrisObjectScale().setSize(scale).setInterpolation(scaleInterpolation))
                .setEdit(new KList<>(edit))
                .setBore(bore)
                .setSmartBore(smartBore);
    }

    public IrisStaticObject setScale(Double scale) {
        this.scale = scale == null ? null : IrisObjectScale.requireValidFactor(scale, "scale");
        return this;
    }

    public double resolveScale(IrisDimension dimension) {
        return scale == null ? dimension.getAllObjectScaleFactor()
                : IrisObjectScale.requireValidFactor(scale, "scale");
    }

    private static void validateEdit(IrisObjectReplace replacement, int index) {
        if (replacement.getFind() == null || replacement.getFind().isEmpty()
                || replacement.getFind().contains(null)) {
            throw new IllegalArgumentException("edit[" + index + "].find must contain block objects.");
        }
        IrisMaterialPalette palette = replacement.getReplace();
        if (palette == null || palette.getPalette() == null || palette.getPalette().isEmpty()
                || palette.getPalette().contains(null)) {
            throw new IllegalArgumentException("edit[" + index + "].replace must contain a block palette.");
        }
        if (!Float.isFinite(replacement.getChance()) || replacement.getChance() < 0 || replacement.getChance() > 1) {
            throw new IllegalArgumentException("edit[" + index + "].chance must be between 0 and 1.");
        }
    }
}
