package com.volmit.service;

/** How the host combines the providers of one {@link Service}. A service chooses one; its javadoc says how the host
 * uses the answers. Providers are always in module id order, each module's in the order it registered them, so the
 * outcome never depends on how modules were discovered.
 */
public enum Providers {
    /** The host asks every provider and combines their answers, such as the objects each module places in a chunk. */
    ALL,
    /** The host asks the providers in order until one answers, such as the module that knows a structure key. A provider
     * that has no answer says so in the service's own terms (an empty result) and the next one is asked.
     */
    FIRST,
    /** A pack has at most one provider. A second module registering one fails the pack load, naming both modules. */
    ONE
}
