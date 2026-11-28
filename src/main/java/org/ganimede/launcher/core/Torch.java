package org.ganimede.launcher.core;

import java.util.HashMap;
import java.util.Map;

/** What the flashlight tap knows: whether the state callback is registered, each flash's last state, the tap waiting for one. */
public final class Torch {
    /** What a tap does now: register for the states first, wait for the first one, or switch. */
    public enum Tap { REGISTER, WAIT, SWITCH }

    private final Map<String, Boolean> on = new HashMap<>();
    private final Pending asked = new Pending();
    private boolean watched;

    public Tap tap() {
        return !watched ? Tap.REGISTER : asked.waiting() ? Tap.WAIT : Tap.SWITCH;
    }

    /** The callback is being registered for flash id: the tap waits for its first state. */
    public void registering(String id) {
        watched = true;
        asked.await(id);
    }

    /** The registration threw: the next tap tries again instead of waiting for a state that never comes. */
    public void registrationFailed(String id) {
        watched = false;
        asked.take(id);
    }

    /** A state report: whether it answers the waiting tap (then the flash is switched to the opposite). */
    public boolean reported(String id, boolean now) {
        on.put(id, now);
        return asked.take(id);
    }

    /** The timeout: whether the waiting tap is still unanswered, to switch from the last known state. */
    public boolean timedOut(String id) {
        return asked.take(id);
    }

    /** The state a switch aims at: the opposite of the last known one ("off" if none came). */
    public boolean target(String id) {
        return !on.getOrDefault(id, false);
    }
}
