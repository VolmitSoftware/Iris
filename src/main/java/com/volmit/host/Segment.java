package com.volmit.host;

import com.volmit.stage.StageKind;

/** Positions [start, end) of the resolved order. An INVOCATION segment holds one stage;
 * a BLOCK segment is one fused block pass.
 */
record Segment(StageKind kind, int start, int end) {}
