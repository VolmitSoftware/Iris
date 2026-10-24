package com.volmit.stage;

import com.volmit.generation.GenerationContext;

/** One chunk generation invocation with access to the host's typed local hunks. */
public interface ChunkStageContext extends GenerationContext, StageContext {}
