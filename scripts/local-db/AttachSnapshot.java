import com.sun.tools.attach.VirtualMachine;
public class AttachSnapshot {
    public static void main(String[] args) throws Exception {
        VirtualMachine vm = VirtualMachine.attach(args[0]);
        try { vm.loadAgent(args[1], args[2]); } finally { vm.detach(); }
        System.out.println("Local snapshot agent request completed; inspect the private manifest for success.");
    }
}
