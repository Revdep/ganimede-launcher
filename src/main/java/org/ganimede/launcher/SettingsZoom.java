package org.ganimede.launcher;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** Lays a view out at full screen size and draws it shrunk by `scale`: a true miniature of the phone. */
final class SettingsZoom extends ViewGroup {
    private final View child;
    private final int stageWidth, stageHeight;
    private float scale;

    SettingsZoom(Context c, View child, float scale, int stageWidth, int stageHeight) {
        super(c);
        this.child = child;
        this.stageWidth = stageWidth;
        this.stageHeight = stageHeight;
        child.setPivotX(0);
        child.setPivotY(0);
        addView(child);
        setScale(scale);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    }

    float getScaleOf() {
        return scale;
    }

    void setScale(float s) {
        if (s == scale) return;
        scale = s;
        child.setScaleX(s);
        child.setScaleY(s);
        requestLayout();
    }

    @Override
    protected void onMeasure(int w, int h) {
        child.measure(MeasureSpec.makeMeasureSpec(stageWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(stageHeight, MeasureSpec.EXACTLY));
        setMeasuredDimension(Math.round(stageWidth * scale), Math.round(stageHeight * scale));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        child.layout(0, 0, child.getMeasuredWidth(), child.getMeasuredHeight());
    }
}
