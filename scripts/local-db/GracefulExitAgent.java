import java.lang.instrument.Instrumentation;
/** Invoked only by Stop-LocalServer.ps1 after checking the managed PID and start time. */
public class GracefulExitAgent {
    public static void agentmain(String argument, Instrumentation instrumentation) {
        Thread exit = new Thread(() -> { try { Thread.sleep(250); } catch (InterruptedException ignored) {} System.exit(0); }, "managed-local-server-stop");
        exit.setDaemon(false); exit.start();
    }
}
