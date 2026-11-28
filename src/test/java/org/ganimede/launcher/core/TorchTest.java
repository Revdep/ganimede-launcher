package org.ganimede.launcher.core;

import static org.ganimede.launcher.Expect.eq;
import static org.ganimede.launcher.Expect.yes;

public class TorchTest {
    public static void testTheFirstTapRegistersThenWaitsForTheStateAndAnswersOnce() {
        Torch t = new Torch();
        eq(Torch.Tap.REGISTER, t.tap(), "nothing known yet: register");
        t.registering("0");
        eq(Torch.Tap.WAIT, t.tap(), "a tap before the first state waits");
        yes(t.reported("0", true), "the first report answers the waiting tap");
        eq(Torch.Tap.SWITCH, t.tap(), "then taps switch");
        yes(!t.target("0"), "from on to off");
        yes(!t.timedOut("0"), "the timeout after the answer switches nothing");
    }

    public static void testARegistrationThatFailedLetsTheNextTapSwitch() {
        Torch t = new Torch();
        t.registering("0");
        t.registrationFailed("0");
        eq(Torch.Tap.REGISTER, t.tap(), "not stuck waiting: the next tap registers again");
        t.registering("0");
        yes(t.timedOut("0"), "no state in time: the waiting tap is answered by the timeout");
        eq(Torch.Tap.SWITCH, t.tap(), "and the tap after it commutes");
        yes(t.target("0"), "from the unknown state taken as off");
    }
}
