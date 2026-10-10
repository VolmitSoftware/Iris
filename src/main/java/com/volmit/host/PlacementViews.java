package com.volmit.host;

import com.volmit.block.BlockState;
import com.volmit.mantle.BlockBox;
import com.volmit.mantle.Mantle;
import com.volmit.mantle.MantleEdit;
import com.volmit.mantle.MantleKey;
import com.volmit.mantle.MantleLayer;
import com.volmit.mantle.MantleVisitor;
import com.volmit.placement.PlacedBlock;
import com.volmit.placement.PlacedMarker;
import com.volmit.placement.PlacementAccess;
import com.volmit.placement.PlacementEdit;
import java.util.Objects;

/** Internal stage views: bound access to an area and hide transaction ownership. */
final class PlacementViews {
    private PlacementViews() {}

    static PlacementAccess restrict(PlacementEdit edit, BlockBox bounds) {
        return new RestrictedPlacementAccess(edit, bounds);
    }

    private static Mantle restrictMantle(Mantle parent, BlockBox bounds) {
        checkArea(parent.bounds(), bounds);
        return new RestrictedMantle(parent, bounds);
    }

    private static void checkPosition(BlockBox bounds, int x, int y, int z) {
        if (!bounds.contains(x, y, z)) {
            throw new IndexOutOfBoundsException("Placement access outside declared reach");
        }
    }

    private static void checkArea(BlockBox bounds, BlockBox area) {
        if (!bounds.contains(area)) {
            throw new IndexOutOfBoundsException("Placement area outside declared reach");
        }
    }

    private static final class RestrictedPlacementAccess implements PlacementAccess {
        private final PlacementEdit edit;
        private final BlockBox allowedBounds;

        private RestrictedPlacementAccess(PlacementEdit edit, BlockBox allowedBounds) {
            this.edit = Objects.requireNonNull(edit, "edit");
            this.allowedBounds = Objects.requireNonNull(allowedBounds, "bounds");
        }

        @Override
        public BlockBox bounds() {
            edit.bounds();
            return allowedBounds;
        }

        @Override
        public BlockState get(int x, int y, int z) {
            checkPosition(bounds(), x, y, z);
            return edit.get(x, y, z);
        }

        @Override
        public void set(PlacedBlock block) {
            var position = block.position();
            checkPosition(bounds(), position.x(), position.y(), position.z());
            edit.set(block);
        }

        @Override
        public void marker(PlacedMarker marker) {
            var position = marker.position();
            checkPosition(bounds(), position.x(), position.y(), position.z());
            edit.marker(marker);
        }

        @Override
        public Mantle mantle() {
            return restrictMantle(edit.mantle(), bounds());
        }
    }

    private static final class RestrictedMantle implements Mantle {
        private final Mantle parent;
        private final BlockBox allowedBounds;

        private RestrictedMantle(Mantle parent, BlockBox allowedBounds) {
            this.parent = Objects.requireNonNull(parent, "parent");
            this.allowedBounds = Objects.requireNonNull(allowedBounds, "bounds");
        }

        @Override
        public BlockBox bounds() {
            parent.bounds();
            return allowedBounds;
        }

        @Override
        public MantleEdit edit() {
            bounds();
            throw new UnsupportedOperationException("Placement owns the mantle edit");
        }

        @Override
        public <T> MantleLayer<T> layer(MantleKey<T> key) {
            bounds();
            return new RestrictedMantleLayer<>(this, parent.layer(key));
        }

        @Override
        public MantleLayer<BlockState> blocks() {
            bounds();
            return new RestrictedMantleLayer<>(this, parent.blocks());
        }
    }

    private static final class RestrictedMantleLayer<T> implements MantleLayer<T> {
        private final RestrictedMantle owner;
        private final MantleLayer<T> layer;

        private RestrictedMantleLayer(RestrictedMantle owner, MantleLayer<T> layer) {
            this.owner = Objects.requireNonNull(owner, "owner");
            this.layer = Objects.requireNonNull(layer, "layer");
        }

        @Override
        public T get(int x, int y, int z) {
            checkPosition(owner.bounds(), x, y, z);
            return layer.get(x, y, z);
        }

        @Override
        public void set(int x, int y, int z, T value) {
            checkPosition(owner.bounds(), x, y, z);
            layer.set(x, y, z, value);
        }

        @Override
        public void remove(int x, int y, int z) {
            checkPosition(owner.bounds(), x, y, z);
            layer.remove(x, y, z);
        }

        @Override
        public void visit(BlockBox area, MantleVisitor<T> visitor) {
            checkArea(owner.bounds(), area);
            layer.visit(area, Objects.requireNonNull(visitor, "visitor"));
        }

        @Override
        public void clear(BlockBox area) {
            checkArea(owner.bounds(), area);
            layer.clear(area);
        }
    }
}
