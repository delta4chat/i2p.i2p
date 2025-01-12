package net.i2p;

/**
 * @since 0.9.49
 */
public class TestContext extends I2PAppContext {

    public TestContext() {
        TestContext.setGlobalContext(this);
    }

    /**
     * Allows overriding the existing I2PAppContext with a test context whose fields we may mock as we like
     *
     * @param ctx Our test context to replace the global context with
     */
    public static void setGlobalContext(TestContext ctx) {
        _globalAppContext = ctx;
    }
}
