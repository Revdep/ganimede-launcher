package org.ganimede.launcher.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The latest value per key, at most max keys waiting: a re-post replaces its key, a flood of new keys is refused. */
public final class Latest<T> {
    private final Map<String, T> waiting = new LinkedHashMap<>();
    private final int max;

    public Latest(int max) {
        this.max = max;
    }

    /** False when key is new and the roof is reached: nothing is kept. */
    public synchronized boolean offer(String key, T value) {
        if (!waiting.containsKey(key) && waiting.size() >= max) return false;
        waiting.put(key, value);
        return true;
    }

    /** Everything waiting, oldest key first; empties it. */
    public synchronized List<T> drain() {
        List<T> out = new ArrayList<>(waiting.values());
        waiting.clear();
        return out;
    }
}
