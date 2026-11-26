package org.ganimede.fixture;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

/** Visible target for SHOW_ALARMS, protected like the Clock activity on affected phones. */
public final class Alarms extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        TextView text = new TextView(this);
        text.setText("Sveglie fixture");
        text.setGravity(Gravity.CENTER);
        setContentView(text);
    }
}
