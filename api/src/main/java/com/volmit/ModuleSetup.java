package com.volmit;

import com.volmit.stage.StageGraph;

/** What a module gets in {@link IrisModule#setup}: the stage graph it defines its stages in, the registry for
 * everything else, and its own log. Stages and registrations are accepted only while {@code setup} runs; the log
 * may be kept and used later, such as from lifecycle hooks.
 */
public interface ModuleSetup {
    StageGraph stages();

    FeatureRegistry registry();

    /** This module's log, the same one its stages and lifecycle hooks see through their world. */
    ModuleLog log();
}
