package org.ganimede.launcher.core;

import static org.ganimede.launcher.Expect.yes;

public class ImageLimitsTest {
    public static void testDeclaredDimensionsHaveSideAndPixelCaps() {
        yes(ImageLimits.acceptable(16_384, 1) && !ImageLimits.acceptable(16_385, 1),
                "16,384 pixels is the longest accepted side");
        yes(ImageLimits.acceptable(10_000, 10_000) && !ImageLimits.acceptable(10_001, 10_000),
                "100 million is the largest accepted pixel count");
        yes(!ImageLimits.acceptable(0, 1) && !ImageLimits.acceptable(1, 0) && !ImageLimits.acceptable(-1, 1),
                "dimensions must be positive");
        yes(!ImageLimits.acceptable(Integer.MAX_VALUE, Integer.MAX_VALUE),
                "the pixel-count check cannot overflow");
    }
}
