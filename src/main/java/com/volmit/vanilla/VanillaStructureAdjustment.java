package com.volmit.vanilla;

import java.util.Objects;
import java.util.Optional;

/** What a module changes about the platform's own starts of one structure, the ones the platform places where it
 * would on its own: how far they move up or down, a band of world heights to move them into, whether they keep the
 * height the platform chose, and how they {@link VanillaStructureFit fit} the terrain.
 *
 * <p>Precedence, as the host applies it: keeping the platform's height wins over a band, a band wins over the
 * host's own repositioning of buried structures, and the shift applies on top of whichever height results.
 * Structures with a fixed platform alignment (an ocean monument's sea level) keep it.
 */
public final class VanillaStructureAdjustment {
    /** The platform's starts as the platform makes them. */
    public static final VanillaStructureAdjustment NONE = new VanillaStructureAdjustment(0, null, false, VanillaStructureFit.SOURCE);

    private final int yShift;
    private final YBand yBand;
    private final boolean keepsSourceY;
    private final VanillaStructureFit fit;

    private VanillaStructureAdjustment(int yShift, YBand yBand, boolean keepsSourceY, VanillaStructureFit fit) {
        this.yShift = yShift;
        this.yBand = yBand;
        this.keepsSourceY = keepsSourceY;
        this.fit = fit;
    }

    /** This adjustment, moving the starts {@code blocks} further up (negative: down). */
    public VanillaStructureAdjustment shifted(int blocks) {
        return new VanillaStructureAdjustment(Math.addExact(yShift, blocks), yBand, keepsSourceY, fit);
    }

    public VanillaStructureAdjustment withYBand(YBand band) {
        return new VanillaStructureAdjustment(yShift, Objects.requireNonNull(band, "band"), keepsSourceY, fit);
    }

    /** This adjustment, keeping the height the platform chose instead of the host's repositioning of buried structures. */
    public VanillaStructureAdjustment keepingSourceY() {
        return new VanillaStructureAdjustment(yShift, yBand, true, fit);
    }

    public VanillaStructureAdjustment withFit(VanillaStructureFit fit) {
        return new VanillaStructureAdjustment(yShift, yBand, keepsSourceY, Objects.requireNonNull(fit, "fit"));
    }

    /** Blocks the starts move up; negative moves them down. */
    public int yShift() {
        return yShift;
    }

    public Optional<YBand> yBand() {
        return Optional.ofNullable(yBand);
    }

    public boolean keepsSourceY() {
        return keepsSourceY;
    }

    public VanillaStructureFit fit() {
        return fit;
    }

    /** Both adjustments applied, {@code later} after this one: shifts add up, keeping the platform's height sticks, and
     * a band or a fit part {@code later} states replaces this one's. The host combines the adjustments of several
     * modules this way, in module id order.
     */
    public VanillaStructureAdjustment then(VanillaStructureAdjustment later) {
        return new VanillaStructureAdjustment(Math.addExact(yShift, later.yShift), later.yBand().orElse(yBand),
                keepsSourceY || later.keepsSourceY, fit.then(later.fit));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof VanillaStructureAdjustment that && yShift == that.yShift
                && Objects.equals(yBand, that.yBand) && keepsSourceY == that.keepsSourceY && fit.equals(that.fit);
    }

    @Override
    public int hashCode() {
        return Objects.hash(yShift, yBand, keepsSourceY, fit);
    }

    @Override
    public String toString() {
        return "VanillaStructureAdjustment[shift " + yShift + ", " + yBand().map(Object::toString).orElse("no band")
                + (keepsSourceY ? ", source Y" : "") + ", " + fit + "]";
    }
}
