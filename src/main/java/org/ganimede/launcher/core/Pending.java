package org.ganimede.launcher.core;

/** A tap waiting for one flash's reported state: the first report for that flash, or the timeout, answers it, once. */
public final class Pending {
    private String id;

    /** Whether a tap is still waiting for its flash's first report (a second tap meanwhile must not start from a guess). */
    public boolean waiting() { return id != null; }

    public void await(String id) { this.id = id; }

    /** Whether this report (or timeout) for id is the one the waiting tap was for; true only the first time. */
    public boolean take(String id) {
        if (this.id == null || !this.id.equals(id)) return false;
        this.id = null;
        return true;
    }
}
