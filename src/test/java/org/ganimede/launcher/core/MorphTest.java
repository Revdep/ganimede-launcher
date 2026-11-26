package org.ganimede.launcher.core;

import static org.ganimede.launcher.Expect.eq;
import static org.ganimede.launcher.Expect.near;
import static org.ganimede.launcher.Expect.yes;

public class MorphTest {
    public static void testStartScaleFitsTheOrigin() {
        eq(0.25f, Morph.startScale(150, 150, 600, 900), "an icon a quarter of the card's width");
        eq(Morph.MIN_SCALE, Morph.startScale(0, 0, 600, 900), "a bare touch point: never a dot");
        eq(Morph.MAX_SCALE, Morph.startScale(600, 900, 600, 900), "as big as the card: it still grows a little");
        eq(Morph.MAX_SCALE, Morph.startScale(10, 10, 0, 0), "a card not laid out yet");
    }

    public static void testPivotCentresTheFoldedCardOnTheOrigin() {
        float start = 100, size = 600, s = 0.25f;
        for (float o : new float[]{50, 400, 1000}) {
            float pivot = start + Morph.pivot(start, size, o, s);
            float centre = pivot + s * (start + size / 2 - pivot); // where the card's middle lands, scaled by s
            near(o, centre, "folded card centred on " + o);
        }
        near(300, Morph.pivot(100, 600, 400, 0.25f), "an origin at the card's middle pivots at the middle");
    }

    public static void testScaleAndOpacityFollowProgress() {
        eq(0.25f, Morph.scale(0.25f, 0), "folded");
        eq(1f, Morph.scale(0.25f, 1), "open");
        near(0.625f, Morph.scale(0.25f, 0.5f), "halfway");
        eq(0f, Morph.alpha(0), "invisible when folded");
        near(0.5f, Morph.alpha(0.15f), "half there at 15% of the way");
        eq(1f, Morph.alpha(0.3f), "fully there from 30% on");
        eq(1f, Morph.alpha(1.3f), "never more than opaque");
    }

    public static void testAnInterruptedTripRunsBackFromWhereItIs() {
        eq(200L, Morph.ms(200, 1, 0), "a whole close");
        eq(60L, Morph.ms(200, 0.3f, 0), "closing an opening caught at 30%");
        eq(175L, Morph.ms(250, 0.3f, 1), "the rest of an opening");
        eq(0L, Morph.ms(250, 1, 1), "nothing left");
    }

    public static void testAPulledSheetFollowsTheFinger() {
        eq(1f, Morph.pulled(0, 800), "not pulled");
        eq(0.75f, Morph.pulled(200, 800), "a quarter down");
        eq(0f, Morph.pulled(900, 800), "past its height");
        eq(1f, Morph.pulled(-50, 800), "pushed up: stays put");
        for (float dy : new float[]{0, 120, 480, 800})
            near(dy, Morph.drop(Morph.pulled(dy, 800), 800), "sits exactly under the finger at " + dy);
    }

    public static void testASheetLeansTowardsWhatOpenedIt() {
        eq(200f, Morph.inside(260, 60, 1000), "an icon at 260 on a sheet from 60");
        eq(0f, Morph.inside(10, 60, 1000), "left of the sheet: its edge");
        eq(1000f, Morph.inside(2000, 60, 1000), "right of the sheet: its edge");
    }

    public static void testEveryProgressFunctionIsMonotonicAndClamped() {
        float scale = -1, alpha = -1, drop = 1e9f;
        for (int i = 0; i <= 20; i++) {
            float p = i / 20f;
            float sc = Morph.scale(0.25f, p), a = Morph.alpha(p), d = Morph.drop(p, 800);
            yes(sc >= scale, "scale never shrinks while opening, at " + p);
            yes(a >= alpha, "alpha never fades while opening, at " + p);
            yes(d <= drop, "a sheet never sinks while opening, at " + p);
            yes(sc >= 0.25f && sc <= 1 && a >= 0 && a <= 1 && d >= 0 && d <= 800, "inside its range at " + p);
            scale = sc; alpha = a; drop = d;
        }
        eq(0f, Morph.alpha(-0.5f), "alpha below 0 is invisible");
        eq(1f, Morph.alpha(5f), "alpha above 1 is opaque");
        eq(1f, Morph.pulled(-100, 800), "pulled up: fully open");
        eq(0f, Morph.pulled(5000, 800), "pulled far past the height: closed");
        float prev = 2;
        for (int dy = -100; dy <= 1000; dy += 50) {
            float v = Morph.pulled(dy, 800);
            yes(v <= prev && v >= 0 && v <= 1, "pulled only closes as the finger goes down, at " + dy);
            prev = v;
        }
    }

    public static void testStaggerGrowsThenStopsAtTenItems() {
        eq(0L, Morph.stagger(0), "the first icon starts at once");
        eq(18L, Morph.stagger(1), "then 18 ms apart");
        eq(180L, Morph.stagger(10), "the tenth is 180 ms in");
        eq(180L, Morph.stagger(40), "later icons do not wait longer");
    }
}
