public class ReplicaCrashSimulator {
    public static void main(String[] args) throws Exception {
        System.out.println("[Simulator] Crashing RM2...");
        String[] command = { "bash", "-c", "lsof -ti:8020 | xargs kill -9" };
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.inheritIO();
        pb.start();
        System.out.println("[Simulator] RM2 crash triggered.");
    }
}