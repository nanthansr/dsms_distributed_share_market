public class FrontEndFailureSimulator {
    public static void main(String[] args) throws Exception {
        System.out.println("[Simulator] Crashing Front-End...");
        String[] command = { "bash", "-c", "lsof -ti:9000 | xargs kill -9" };
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.inheritIO();
        pb.start();
        System.out.println("[Simulator] Front-End terminated.");
    }
}