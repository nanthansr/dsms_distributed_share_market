public class IncorrectReplicaResponseSimulator {
    public static void main(String[] args) throws Exception {
        System.out.println("[Simulator] Launching faulty RM6...");
        ProcessBuilder pb = new ProcessBuilder("java", "dsms.replicas.FaultyReplicaLauncher", "RM6");
        pb.inheritIO();
        pb.start();
        System.out.println("[Simulator] Faulty RM6 launched.");
    }
}