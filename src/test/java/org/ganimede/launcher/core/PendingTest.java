package org.ganimede.launcher.core;

import static org.ganimede.launcher.Expect.yes;

public class PendingTest {
    public static void testTheFirstReportOfTheTappedFlashAnswersTheTapOnce() {
        Pending p = new Pending();
        p.await("0");
        yes(!p.take("1"), "another flash's state does not answer a tap on flash 0");
        yes(p.take("0"), "flash 0's first reported state answers the tap");
        yes(!p.take("0"), "a later report or the timeout does not switch it a second time");
    }

    public static void testNothingWaitsBeforeATap() {
        yes(!new Pending().take("0"), "a state reported with no tap waiting switches nothing");
    }
}
