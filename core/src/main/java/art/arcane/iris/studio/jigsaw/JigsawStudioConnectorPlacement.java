package art.arcane.iris.studio.jigsaw;

import art.arcane.iris.pack.value.IrisDirection;
import art.arcane.iris.pack.value.IrisPosition;
import art.arcane.iris.structure.jigsaw.IrisJigsawConnector;
import art.arcane.iris.structure.jigsaw.JigsawJoint;
import art.arcane.iris.structure.object.IrisObjectRotation;
import art.arcane.iris.studio.jigsaw.JigsawStudioCapture.CapturedConnector;
import art.arcane.iris.studio.jigsaw.JigsawStudioCapture.LocalPosition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

final class JigsawStudioConnectorPlacement {
    static final double MAX_REACH = 64.0D;
    static final String EMPTY_FINAL_STATE = "minecraft:structure_void";

    private static final double EPSILON = 1.0E-4D;
    private static final int MAX_TRAVERSAL_STEPS = 512;
    private static final List<JigsawPlanarDirection> SIDE_ORDER = List.of(
            JigsawPlanarDirection.NORTH,
            JigsawPlanarDirection.EAST,
            JigsawPlanarDirection.SOUTH,
            JigsawPlanarDirection.WEST);

    private JigsawStudioConnectorPlacement() {
    }

    static Optional<Target> target(
            Box box,
            Ray ray,
            Optional<BlockClick> click,
            Set<Cell> connectorCells,
            boolean sneaking
    ) {
        Objects.requireNonNull(box, "Jigsaw connector target box");
        Objects.requireNonNull(ray, "Jigsaw connector target ray");
        Objects.requireNonNull(click, "Jigsaw connector target click");
        Objects.requireNonNull(connectorCells, "Jigsaw connector cells");
        double limit = click.map(value -> entryDistance(
                ray,
                new Box(value.x(), value.y(), value.z(), value.x(), value.y(), value.z()))).orElse(MAX_REACH);
        Optional<Target> resolved = connectorTarget(ray, connectorCells, limit + EPSILON);
        if (resolved.isEmpty() && click.isPresent()) {
            resolved = clickTarget(box, click.get());
        }
        if (resolved.isEmpty()) {
            resolved = boundaryTarget(box, ray, limit);
        }
        return resolved.map(target -> sneaking
                ? new Target(target.x(), target.y(), target.z(), target.facing().reverse())
                : target);
    }

    static Decision decideSpatial(
            boolean primary,
            LocalPosition target,
            IrisDirection facing,
            Map<LocalPosition, IrisDirection> connectors,
            Optional<LocalPosition> selection
    ) {
        boolean occupied = connectors.containsKey(target);
        Optional<LocalPosition> activeSelection = selection.filter(connectors::containsKey);
        if (primary) {
            if (occupied) {
                return Decision.of(Operation.REMOVE, target);
            }
            return activeSelection.isPresent()
                    ? Decision.of(Operation.CLEAR_SELECTION, activeSelection.get())
                    : Decision.message(Operation.NONE, "No connector here. Right-click to place one.");
        }
        if (activeSelection.isEmpty()) {
            return Decision.of(occupied ? Operation.SELECT : Operation.ADD, target);
        }
        LocalPosition selected = activeSelection.get();
        if (selected.equals(target)) {
            return Decision.of(connectors.get(target) == facing ? Operation.DESELECT : Operation.REORIENT, target);
        }
        return Decision.of(occupied ? Operation.SELECT : Operation.MOVE, target);
    }

    static Decision decidePlanar(
            boolean primary,
            LocalPosition target,
            IrisDirection facing,
            JigsawStudioCellDimensions displayDimensions,
            JigsawPlanarTopology topology,
            Map<LocalPosition, IrisDirection> connectors,
            String workcellName
    ) {
        if (primary) {
            IrisDirection connectorFacing = connectors.get(target);
            if (connectorFacing == null) {
                return Decision.message(Operation.NONE, "No connector here. Right-click a side to seat its socket.");
            }
            Optional<JigsawPlanarDirection> side = planarSide(connectorFacing);
            if (side.isPresent() && topology.connects(side.get())
                    && countFacing(connectors, connectorFacing) == 1) {
                return Decision.message(Operation.REFUSE, workcellName + " needs its "
                        + sideName(side.get()) + " socket; planar topology is fixed by the workcell.");
            }
            return Decision.of(Operation.REMOVE, target);
        }
        if (topology.directions().isEmpty()) {
            return Decision.message(Operation.REFUSE, workcellName + " has no connector sockets.");
        }
        Optional<JigsawPlanarDirection> clicked = planarSide(facing);
        JigsawPlanarDirection side = clicked.orElseGet(() -> nearestSide(target, displayDimensions, topology));
        if (!topology.connects(side)) {
            return Decision.message(Operation.REFUSE, workcellName + " connects "
                    + describeSides(topology) + " only; its planar topology is fixed.");
        }
        return new Decision(Operation.RESEAT, target, side, "");
    }

    static int indexAt(List<IrisJigsawConnector> connectors, Frame frame, LocalPosition display) {
        for (int index = 0; index < connectors.size(); index++) {
            if (frame.display(connectors.get(index).getPosition()).equals(display)) {
                return index;
            }
        }
        return -1;
    }

    static List<IrisJigsawConnector> add(
            List<IrisJigsawConnector> connectors,
            Frame frame,
            LocalPosition target,
            IrisDirection facing,
            IrisDirection top,
            Identity identity,
            CellContents contents
    ) {
        if (indexAt(connectors, frame, target) >= 0) {
            throw new IllegalArgumentException("A connector already occupies " + describe(target));
        }
        IrisPosition position = frame.source(target);
        IrisJigsawConnector added = new IrisJigsawConnector()
                .setPosition(position)
                .setDirection(frame.sourceDirection(facing))
                .setTop(frame.sourceDirection(top))
                .setPool(identity.pool())
                .setName(identity.name())
                .setTargetName(identity.targetName())
                .setChannel("")
                .setJoint(identity.joint())
                .setFinalState(finalState(position, EMPTY_FINAL_STATE, contents))
                .setSelectionPriority(0)
                .setPlacementPriority(0);
        List<IrisJigsawConnector> updated = copy(connectors);
        updated.add(added);
        return List.copyOf(updated);
    }

    static List<IrisJigsawConnector> move(
            List<IrisJigsawConnector> connectors,
            Frame frame,
            LocalPosition from,
            LocalPosition to,
            IrisDirection facing,
            IrisDirection top,
            CellContents contents
    ) {
        int index = indexAt(connectors, frame, from);
        if (index < 0) {
            throw new IllegalArgumentException("No connector exists at " + describe(from));
        }
        if (!from.equals(to) && indexAt(connectors, frame, to) >= 0) {
            throw new IllegalArgumentException("A connector already occupies " + describe(to));
        }
        List<IrisJigsawConnector> updated = copy(connectors);
        IrisJigsawConnector moved = updated.get(index);
        IrisPosition position = frame.source(to);
        moved.setPosition(position)
                .setDirection(frame.sourceDirection(facing))
                .setTop(frame.sourceDirection(top));
        if (!from.equals(to)) {
            moved.setFinalState(finalState(position, moved.getFinalState(), contents));
        }
        return List.copyOf(updated);
    }

    static List<IrisJigsawConnector> remove(
            List<IrisJigsawConnector> connectors,
            Frame frame,
            LocalPosition at
    ) {
        int index = indexAt(connectors, frame, at);
        if (index < 0) {
            throw new IllegalArgumentException("No connector exists at " + describe(at));
        }
        List<IrisJigsawConnector> updated = copy(connectors);
        updated.remove(index);
        return List.copyOf(updated);
    }

    static ReseatResult reseat(
            List<IrisJigsawConnector> connectors,
            Frame frame,
            JigsawPlanarDirection displaySide,
            Identity identity,
            CellContents contents
    ) {
        IrisDirection sourceDirection = frame.sourceDirection(displaySide.irisDirection());
        JigsawStudioCellDimensions dimensions = frame.sourceDimensions();
        IrisPosition socket = IrisJigsawConnector.canonicalPlanarPosition(
                new IrisPosition(dimensions.width(), dimensions.height(), dimensions.depth()),
                sourceDirection);
        List<IrisJigsawConnector> updated = copy(connectors);
        int seatedIndex = -1;
        int strays = 0;
        for (int index = 0; index < updated.size(); index++) {
            if (updated.get(index).getDirection() != sourceDirection) {
                continue;
            }
            if (seatedIndex < 0) {
                seatedIndex = index;
            } else {
                strays++;
            }
        }
        if (seatedIndex < 0) {
            updated.add(new IrisJigsawConnector()
                    .setPosition(socket)
                    .setDirection(sourceDirection)
                    .setTop(IrisDirection.UP_POSITIVE_Y)
                    .setPool(identity.pool())
                    .setName(identity.name())
                    .setTargetName(identity.targetName())
                    .setChannel("")
                    .setJoint(identity.joint())
                    .setFinalState(finalState(socket, EMPTY_FINAL_STATE, contents))
                    .setSelectionPriority(0)
                    .setPlacementPriority(0));
            return new ReseatResult(List.copyOf(updated), true, 0);
        }
        IrisJigsawConnector seated = updated.get(seatedIndex);
        boolean alreadySeated = socket.equals(seated.getPosition())
                && seated.getTop() == IrisDirection.UP_POSITIVE_Y;
        if (alreadySeated && strays == 0) {
            return new ReseatResult(List.copyOf(connectors), false, 0);
        }
        if (!socket.equals(seated.getPosition())) {
            seated.setPosition(socket)
                    .setFinalState(finalState(socket, seated.getFinalState(), contents));
        }
        seated.setTop(IrisDirection.UP_POSITIVE_Y);
        List<IrisJigsawConnector> retained = new ArrayList<>(updated.size() - strays);
        for (int index = 0; index < updated.size(); index++) {
            IrisJigsawConnector connector = updated.get(index);
            if (index == seatedIndex || connector.getDirection() != sourceDirection) {
                retained.add(connector);
            }
        }
        return new ReseatResult(List.copyOf(retained), true, strays);
    }

    static Set<LocalPosition> changedPositions(
            List<IrisJigsawConnector> before,
            List<IrisJigsawConnector> after,
            Frame frame
    ) {
        Map<LocalPosition, CapturedConnector> previous = displayed(before, frame);
        Map<LocalPosition, CapturedConnector> current = displayed(after, frame);
        Set<LocalPosition> changed = new HashSet<>();
        for (Map.Entry<LocalPosition, CapturedConnector> entry : previous.entrySet()) {
            if (!entry.getValue().equals(current.get(entry.getKey()))) {
                changed.add(entry.getKey());
            }
        }
        for (Map.Entry<LocalPosition, CapturedConnector> entry : current.entrySet()) {
            if (!entry.getValue().equals(previous.get(entry.getKey()))) {
                changed.add(entry.getKey());
            }
        }
        return Set.copyOf(changed);
    }

    static String describe(LocalPosition position) {
        return position.x() + "," + position.y() + "," + position.z();
    }

    static String sideName(JigsawPlanarDirection side) {
        return side.name().toLowerCase(Locale.ROOT);
    }

    private static Map<LocalPosition, CapturedConnector> displayed(List<IrisJigsawConnector> connectors, Frame frame) {
        Map<LocalPosition, CapturedConnector> displayed = new HashMap<>(connectors.size());
        for (IrisJigsawConnector connector : connectors) {
            displayed.put(frame.display(connector.getPosition()), CapturedConnector.from(connector));
        }
        return displayed;
    }

    private static List<IrisJigsawConnector> copy(List<IrisJigsawConnector> connectors) {
        List<IrisJigsawConnector> copied = new ArrayList<>(connectors.size() + 1);
        for (IrisJigsawConnector connector : connectors) {
            copied.add(CapturedConnector.from(connector).toConnector());
        }
        return copied;
    }

    private static String finalState(IrisPosition position, String whenEmpty, CellContents contents) {
        Optional<CellContent> content = contents.at(position);
        if (content.isEmpty()) {
            return whenEmpty;
        }
        if (content.get().blockEntity()) {
            throw new IllegalArgumentException("Connectors cannot replace the block entity at "
                    + position.getX() + "," + position.getY() + "," + position.getZ()
                    + "; clear that block first.");
        }
        return content.get().state();
    }

    private static int countFacing(Map<LocalPosition, IrisDirection> connectors, IrisDirection facing) {
        int count = 0;
        for (IrisDirection direction : connectors.values()) {
            if (direction == facing) {
                count++;
            }
        }
        return count;
    }

    private static Optional<JigsawPlanarDirection> planarSide(IrisDirection direction) {
        return switch (direction) {
            case NORTH_NEGATIVE_Z -> Optional.of(JigsawPlanarDirection.NORTH);
            case EAST_POSITIVE_X -> Optional.of(JigsawPlanarDirection.EAST);
            case SOUTH_POSITIVE_Z -> Optional.of(JigsawPlanarDirection.SOUTH);
            case WEST_NEGATIVE_X -> Optional.of(JigsawPlanarDirection.WEST);
            case UP_POSITIVE_Y, DOWN_NEGATIVE_Y -> Optional.empty();
        };
    }

    private static JigsawPlanarDirection nearestSide(
            LocalPosition target,
            JigsawStudioCellDimensions dimensions,
            JigsawPlanarTopology topology
    ) {
        JigsawPlanarDirection nearest = null;
        int nearestDistance = Integer.MAX_VALUE;
        for (JigsawPlanarDirection side : SIDE_ORDER) {
            if (!topology.connects(side)) {
                continue;
            }
            int distance = switch (side) {
                case NORTH -> target.z();
                case EAST -> dimensions.width() - 1 - target.x();
                case SOUTH -> dimensions.depth() - 1 - target.z();
                case WEST -> target.x();
            };
            if (distance < nearestDistance) {
                nearest = side;
                nearestDistance = distance;
            }
        }
        return Objects.requireNonNull(nearest, "Planar topology has no sides");
    }

    private static String describeSides(JigsawPlanarTopology topology) {
        List<String> names = new ArrayList<>(4);
        for (JigsawPlanarDirection side : SIDE_ORDER) {
            if (topology.connects(side)) {
                names.add(sideName(side));
            }
        }
        if (names.size() == 1) {
            return names.getFirst();
        }
        if (names.size() == 2) {
            return names.get(0) + " and " + names.get(1);
        }
        return String.join(", ", names.subList(0, names.size() - 1)) + ", and " + names.getLast();
    }

    static double entryDistance(Ray ray, Box box) {
        double near = 0.0D;
        double far = MAX_REACH;
        double[] origin = {ray.originX(), ray.originY(), ray.originZ()};
        double[] direction = {ray.directionX(), ray.directionY(), ray.directionZ()};
        double[] minimum = {box.minX(), box.minY(), box.minZ()};
        double[] maximum = {box.maxX() + 1.0D, box.maxY() + 1.0D, box.maxZ() + 1.0D};
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(direction[axis]) < 1.0E-12D) {
                if (origin[axis] < minimum[axis] || origin[axis] >= maximum[axis]) {
                    return MAX_REACH;
                }
                continue;
            }
            double first = (minimum[axis] - origin[axis]) / direction[axis];
            double second = (maximum[axis] - origin[axis]) / direction[axis];
            near = Math.max(near, Math.min(first, second));
            far = Math.min(far, Math.max(first, second));
        }
        return near <= far ? near : MAX_REACH;
    }

    private static Optional<Target> connectorTarget(Ray ray, Set<Cell> cells, double limit) {
        if (cells.isEmpty()) {
            return Optional.empty();
        }
        int x = (int) Math.floor(ray.originX());
        int y = (int) Math.floor(ray.originY());
        int z = (int) Math.floor(ray.originZ());
        int stepX = (int) Math.signum(ray.directionX());
        int stepY = (int) Math.signum(ray.directionY());
        int stepZ = (int) Math.signum(ray.directionZ());
        double maxX = boundaryDistance(ray.originX(), ray.directionX(), x);
        double maxY = boundaryDistance(ray.originY(), ray.directionY(), y);
        double maxZ = boundaryDistance(ray.originZ(), ray.directionZ(), z);
        double deltaX = stepX == 0 ? Double.POSITIVE_INFINITY : 1.0D / Math.abs(ray.directionX());
        double deltaY = stepY == 0 ? Double.POSITIVE_INFINITY : 1.0D / Math.abs(ray.directionY());
        double deltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : 1.0D / Math.abs(ray.directionZ());
        for (int step = 0; step < MAX_TRAVERSAL_STEPS; step++) {
            double distance;
            IrisDirection entered;
            if (maxX < maxY && maxX < maxZ) {
                distance = maxX;
                x += stepX;
                maxX += deltaX;
                entered = stepX > 0 ? IrisDirection.WEST_NEGATIVE_X : IrisDirection.EAST_POSITIVE_X;
            } else if (maxY < maxZ) {
                distance = maxY;
                y += stepY;
                maxY += deltaY;
                entered = stepY > 0 ? IrisDirection.DOWN_NEGATIVE_Y : IrisDirection.UP_POSITIVE_Y;
            } else {
                distance = maxZ;
                z += stepZ;
                maxZ += deltaZ;
                entered = stepZ > 0 ? IrisDirection.NORTH_NEGATIVE_Z : IrisDirection.SOUTH_POSITIVE_Z;
            }
            if (distance > limit) {
                return Optional.empty();
            }
            if (cells.contains(new Cell(x, y, z))) {
                return Optional.of(new Target(x, y, z, entered));
            }
        }
        return Optional.empty();
    }

    private static double boundaryDistance(double origin, double direction, int cell) {
        if (direction > 0.0D) {
            return (cell + 1 - origin) / direction;
        }
        if (direction < 0.0D) {
            return (origin - cell) / -direction;
        }
        return Double.POSITIVE_INFINITY;
    }

    private static Optional<Target> clickTarget(Box box, BlockClick click) {
        if (box.contains(click.x(), click.y(), click.z())) {
            return Optional.of(new Target(click.x(), click.y(), click.z(), click.face()));
        }
        int adjacentX = click.x() + click.face().x();
        int adjacentY = click.y() + click.face().y();
        int adjacentZ = click.z() + click.face().z();
        if (box.contains(adjacentX, adjacentY, adjacentZ)) {
            return Optional.of(new Target(adjacentX, adjacentY, adjacentZ, click.face().reverse()));
        }
        return Optional.empty();
    }

    private static Optional<Target> boundaryTarget(Box box, Ray ray, double limit) {
        double[] origin = {ray.originX(), ray.originY(), ray.originZ()};
        double[] direction = {ray.directionX(), ray.directionY(), ray.directionZ()};
        double[] minimum = {box.minX(), box.minY(), box.minZ()};
        double[] maximum = {box.maxX() + 1.0D, box.maxY() + 1.0D, box.maxZ() + 1.0D};
        double near = Double.NEGATIVE_INFINITY;
        double far = Double.POSITIVE_INFINITY;
        int nearAxis = -1;
        int farAxis = -1;
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(direction[axis]) < 1.0E-12D) {
                if (origin[axis] < minimum[axis] || origin[axis] >= maximum[axis]) {
                    return Optional.empty();
                }
                continue;
            }
            double first = (minimum[axis] - origin[axis]) / direction[axis];
            double second = (maximum[axis] - origin[axis]) / direction[axis];
            double axisNear = Math.min(first, second);
            double axisFar = Math.max(first, second);
            if (axisNear > near) {
                near = axisNear;
                nearAxis = axis;
            }
            if (axisFar < far) {
                far = axisFar;
                farAxis = axis;
            }
        }
        if (near > far || far < 0.0D) {
            return Optional.empty();
        }
        boolean inside = near < 0.0D;
        double distance = inside ? far - EPSILON : near + EPSILON;
        int axis = inside ? farAxis : nearAxis;
        if (axis < 0 || !inside && near > limit) {
            return Optional.empty();
        }
        boolean positive = direction[axis] > 0.0D;
        IrisDirection facing = axisDirection(axis, inside == positive);
        int x = clamp((int) Math.floor(origin[0] + direction[0] * distance), box.minX(), box.maxX());
        int y = clamp((int) Math.floor(origin[1] + direction[1] * distance), box.minY(), box.maxY());
        int z = clamp((int) Math.floor(origin[2] + direction[2] * distance), box.minZ(), box.maxZ());
        return Optional.of(new Target(x, y, z, facing));
    }

    private static IrisDirection axisDirection(int axis, boolean positive) {
        return switch (axis) {
            case 0 -> positive ? IrisDirection.EAST_POSITIVE_X : IrisDirection.WEST_NEGATIVE_X;
            case 1 -> positive ? IrisDirection.UP_POSITIVE_Y : IrisDirection.DOWN_NEGATIVE_Y;
            case 2 -> positive ? IrisDirection.SOUTH_POSITIVE_Z : IrisDirection.NORTH_NEGATIVE_Z;
            default -> throw new IllegalStateException("Unreachable Jigsaw connector axis " + axis);
        };
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    enum Operation {
        SELECT,
        DESELECT,
        CLEAR_SELECTION,
        ADD,
        MOVE,
        REORIENT,
        REMOVE,
        RESEAT,
        REFUSE,
        NONE
    }

    record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        static Box of(JigsawStudioBounds bounds, JigsawStudioCellDimensions dimensions) {
            return new Box(
                    bounds.originX(),
                    bounds.originY(),
                    bounds.originZ(),
                    bounds.originX() + dimensions.width() - 1,
                    bounds.originY() + dimensions.height() - 1,
                    bounds.originZ() + dimensions.depth() - 1);
        }

        boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }

        LocalPosition local(Target target) {
            return new LocalPosition(target.x() - minX, target.y() - minY, target.z() - minZ);
        }

        Cell world(LocalPosition position) {
            return new Cell(minX + position.x(), minY + position.y(), minZ + position.z());
        }
    }

    record Ray(double originX, double originY, double originZ, double directionX, double directionY, double directionZ) {
        Ray {
            double length = Math.sqrt(directionX * directionX + directionY * directionY + directionZ * directionZ);
            if (!(length > 1.0E-9D)) {
                throw new IllegalArgumentException("Jigsaw connector ray direction cannot be zero");
            }
            directionX /= length;
            directionY /= length;
            directionZ /= length;
        }
    }

    record BlockClick(int x, int y, int z, IrisDirection face) {
        BlockClick {
            Objects.requireNonNull(face, "Jigsaw connector clicked face");
        }
    }

    record Cell(int x, int y, int z) {
    }

    record Target(int x, int y, int z, IrisDirection facing) {
        Target {
            Objects.requireNonNull(facing, "Jigsaw connector target facing");
        }
    }

    record Decision(Operation operation, LocalPosition position, JigsawPlanarDirection side, String message) {
        Decision {
            Objects.requireNonNull(operation, "Jigsaw connector decision operation");
            message = message == null ? "" : message;
        }

        static Decision of(Operation operation, LocalPosition position) {
            return new Decision(operation, position, null, "");
        }

        static Decision message(Operation operation, String message) {
            return new Decision(operation, null, null, message);
        }
    }

    record Frame(JigsawStudioCellDimensions sourceDimensions, int quarterTurns) {
        Frame {
            Objects.requireNonNull(sourceDimensions, "Jigsaw connector frame dimensions");
            quarterTurns = Math.floorMod(quarterTurns, 4);
        }

        JigsawStudioCellDimensions displayDimensions() {
            return (quarterTurns & 1) == 0
                    ? sourceDimensions
                    : new JigsawStudioCellDimensions(
                            sourceDimensions.depth(),
                            sourceDimensions.height(),
                            sourceDimensions.width());
        }

        LocalPosition display(IrisPosition source) {
            return JigsawStudioCapture.forwardPosition(
                    source.getX(),
                    source.getY(),
                    source.getZ(),
                    sourceDimensions.width(),
                    sourceDimensions.depth(),
                    quarterTurns);
        }

        IrisPosition source(LocalPosition display) {
            LocalPosition position = JigsawStudioCapture.inversePosition(
                    display.x(),
                    display.y(),
                    display.z(),
                    sourceDimensions.width(),
                    sourceDimensions.depth(),
                    quarterTurns);
            return new IrisPosition(position.x(), position.y(), position.z());
        }

        IrisDirection sourceDirection(IrisDirection display) {
            return quarterTurns == 0 ? display : IrisObjectRotation.of(0, 90.0D * quarterTurns, 0).rotate(display);
        }

        IrisDirection displayDirection(IrisDirection source) {
            return quarterTurns == 0 ? source : IrisObjectRotation.of(0, -90.0D * quarterTurns, 0).rotate(source);
        }
    }

    record Identity(String pool, String name, String targetName, JigsawJoint joint) {
        Identity {
            Objects.requireNonNull(pool, "Jigsaw connector default pool");
            Objects.requireNonNull(name, "Jigsaw connector default name");
            Objects.requireNonNull(targetName, "Jigsaw connector default target");
            Objects.requireNonNull(joint, "Jigsaw connector default joint");
        }
    }

    record CellContent(String state, boolean blockEntity) {
        CellContent {
            Objects.requireNonNull(state, "Jigsaw connector cell state");
        }
    }

    @FunctionalInterface
    interface CellContents {
        Optional<CellContent> at(IrisPosition source);
    }

    record ReseatResult(List<IrisJigsawConnector> connectors, boolean changed, int removedStrays) {
        ReseatResult {
            connectors = List.copyOf(connectors);
        }
    }
}
