package com.volmit.stage;

import com.volmit.generation.MantleGenerationContext;

/** One mantle invocation for one chunk: the mantle around it, without a block hunk. */
public interface MantleStageContext extends MantleGenerationContext, StageContext {}
