public class StaleReplicaSimulator {
    public static void main(String[] args) throws Exception {
        System.out.println("[Simulator] Starting stale replica without sync...");
        ProcessBuilder pb = new ProcessBuilder("java", "dsms.replicas.ReplicaLauncher", "RM5");
        pb.inheritIO();
        pb.start();
        System.out.println("[Simulator] RM5 started without WSDL sync.");
    }
}