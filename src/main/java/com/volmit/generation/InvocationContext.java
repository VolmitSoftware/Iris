package com.volmit.generation;

import com.volmit.resource.PackResources;
import java.util.random.RandomGenerator;

/** Host-owned invocation inputs. RNG and views must not be retained or used by detached tasks.
 * The host derives streams from world seed, stable feature/occurrence identity and position;
 * module discovery order must not affect them. Keeping legacy random behavior is adapter work.
 */
public interface InvocationContext {
    long worldSeed();
    PackResources resources();
    RandomGenerator random();
}
