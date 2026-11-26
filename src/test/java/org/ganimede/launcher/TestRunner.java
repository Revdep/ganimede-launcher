package org.ganimede.launcher;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Runs every {@code public static void test...()} method of the test classes it is given, with assertions on,
 * and fails the build when one throws, when a test method is written so that it would be skipped, or when a class runs none. No libraries: the tests only need plain Java (see build.gradle).
 */
public final class TestRunner {
    public static void main(String[] classes) throws Exception {
        boolean on = false;
        assert on = true;
        if (!on) throw new IllegalStateException("assertions are off: bare asserts in the tests would check nothing (run with -ea)");
        int run = 0, failed = 0;
        for (String name : classes) {
            Method[] methods = Class.forName(name).getDeclaredMethods();
            Arrays.sort(methods, Comparator.comparing(Method::getName));
            int inClass = 0;
            for (Method m : methods) {
                if (!m.getName().startsWith("test")) continue;
                int mod = m.getModifiers();
                if (!Modifier.isPublic(mod) || !Modifier.isStatic(mod) || m.getParameterCount() != 0 || m.getReturnType() != void.class) {
                    failed++; // a test written this way would never run
                    System.err.println("FAIL " + name + "." + m.getName() + ": not public static void with no parameters, so it is skipped");
                    continue;
                }
                run++;
                inClass++;
                try {
                    m.invoke(null);
                } catch (InvocationTargetException e) {
                    failed++;
                    System.err.println("FAIL " + name + "." + m.getName() + ": " + e.getCause());
                    for (StackTraceElement s : e.getCause().getStackTrace())
                        if (s.getClassName().startsWith("org.ganimede")) System.err.println("    at " + s);
                }
            }
            if (inClass == 0) {
                failed++;
                System.err.println("FAIL " + name + ": no test method ran");
            }
        }
        System.out.println(run + " tests, " + failed + " failed");
        if (failed > 0 || run == 0) System.exit(1);
    }

    private TestRunner() {}
}
