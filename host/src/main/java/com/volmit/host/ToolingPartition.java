package com.volmit.host;

import java.util.List;
import java.util.Optional;

/** Which classes of a pack are tooling parts, and which of them this host checks. A class is a part class when it lies in
 * a package of a part any jar of the pack declares; every other class is generation code. Generation code and the parts
 * of the platform the host runs on are checked, the parts of other platforms are skipped unread.
 */
final class ToolingPartition {
    private final List<ToolingPart> parts;
    private final Optional<PlatformApi> runningPlatform;

    ToolingPartition(List<ModuleJar> jars, Optional<PlatformApi> runningPlatform) {
        this.parts = jars.stream().flatMap(jar -> jar.toolingParts().stream()).toList();
        this.runningPlatform = runningPlatform;
    }

    Optional<ToolingPart> partOf(String className) {
        return parts.stream().filter(part -> part.contains(className)).findFirst();
    }

    /** Whether the class is checked: generation code, or a part of the platform the host runs on. */
    boolean isChecked(String className) {
        return partOf(className).map(this::runsOnThisPlatform).orElse(true);
    }

    /** The platform of the part a reference between these classes involves, or empty when it involves none. */
    String platformInvolvedIn(String source, String target) {
        return partOf(source).or(() -> partOf(target)).map(ToolingPart::platform).orElse("");
    }

    PlatformApi runningPlatform() {
        return runningPlatform.orElseThrow(() -> new IllegalStateException("No tooling part is checked without a running platform"));
    }

    private boolean runsOnThisPlatform(ToolingPart part) {
        return runningPlatform.map(running -> running.platform().name().equals(part.platform())).orElse(false);
    }
}
