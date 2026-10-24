package com.volmit.config;

/** Where a pack declares settings, from the least to the most specific: the dimension, a region, a biome, and an
 * object placement. {@link Feature#targets()} names the levels a feature may be written at, and every
 * {@link FeatureSettings.Entry} carries the level that declared it. Settings stacks keep this order.
 */
public enum Level { DIMENSION, REGION, BIOME, PLACEMENT }
