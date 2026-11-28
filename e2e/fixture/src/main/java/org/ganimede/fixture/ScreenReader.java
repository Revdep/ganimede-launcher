package org.ganimede.fixture;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.regex.Pattern;

/**
 * Stands in for TalkBack, which a shell cannot drive: `am broadcast -a org.ganimede.fixture.A11Y -p org.ganimede.fixture
 * --es desc <content-desc prefix> --es mode list|click|label [--es label <regex>]` finds the first node of any window whose
 * content description starts with desc, then logs (tag FixtureA11y) "action label=..." per action it exposes, or performs
 * ACTION_CLICK, or the first action whose label matches; always ends with "done performed=true|false". Every announcement is logged as "announce [text]". Once bound it logs "connected".
 */
public final class ScreenReader extends AccessibilityService {
    private static final String TAG = "FixtureA11y";

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { run(i); }
    };

    @Override protected void onServiceConnected() {
        registerReceiver(receiver, new IntentFilter("org.ganimede.fixture.A11Y"), "android.permission.DUMP", null, Context.RECEIVER_EXPORTED);
        Log.i(TAG, "connected"); // e2e a11y_on waits for this line: the system bound it and it is listening
    }

    @Override public boolean onUnbind(Intent i) { // a reconnect must not leave two receivers acting twice
        unregisterReceiver(receiver);
        return super.onUnbind(i);
    }

    private void run(Intent i) {
        String desc = i.getStringExtra("desc"), mode = i.getStringExtra("mode"), label = i.getStringExtra("label");
        AccessibilityNodeInfo node = null;
        for (AccessibilityWindowInfo w : getWindows()) {
            AccessibilityNodeInfo root = w.getRoot();
            if (root != null && (node = find(root, desc)) != null) break;
        }
        boolean performed = false;
        if (node == null) Log.i(TAG, "no node " + desc);
        else if ("click".equals(mode)) performed = node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        else for (AccessibilityAction a : node.getActionList()) {
            if (a.getLabel() == null) continue;
            if ("list".equals(mode)) Log.i(TAG, "action label=" + a.getLabel());
            else if ("label".equals(mode) && !performed
                    && Pattern.compile(label, Pattern.CASE_INSENSITIVE).matcher(a.getLabel()).find())
                performed = node.performAction(a.getId());
        }
        Log.i(TAG, "done performed=" + performed);
    }

    private static AccessibilityNodeInfo find(AccessibilityNodeInfo n, String desc) {
        if (n.getContentDescription() != null && n.getContentDescription().toString().startsWith(desc)) return n;
        for (int k = 0; k < n.getChildCount(); k++) {
            AccessibilityNodeInfo child = n.getChild(k), hit = child == null ? null : find(child, desc);
            if (hit != null) return hit;
        }
        return null;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() == AccessibilityEvent.TYPE_ANNOUNCEMENT) Log.i(TAG, "announce " + e.getText());
    }
    @Override public void onInterrupt() { }
}
