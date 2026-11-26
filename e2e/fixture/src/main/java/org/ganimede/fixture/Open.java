package org.ganimede.fixture;

import android.app.Activity;
import android.os.Bundle;

/** The fixture's launcher entry, so the e2e can make it a favourite: it closes at once. */
public final class Open extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        finish();
    }
}
