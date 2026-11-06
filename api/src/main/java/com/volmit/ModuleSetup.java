package com.volmit;

import com.volmit.stage.StageGraph;
import com.volmit.tooling.ToolingRegistry;

/** What a module gets in {@link IrisModule#setup}: the stage graph it defines its stages in, the registry for
 * everything else it adds to generation, the registry for its studio tools, and its own log. Stages and registrations
 * are accepted only while {@code setup} runs; the log may be kept and used later, such as from lifecycle hooks.
 */
public interface ModuleSetup {
    StageGraph stages();

    FeatureRegistry registry();

    ToolingRegistry tooling();

    /** This module's log, the same one its stages and lifecycle hooks see through their world. */
    ModuleLog log();
}
