public class SequencerCrashSimulator {
    public static void main(String[] args) throws Exception {
        System.out.println("[Simulator] Crashing Sequencer...");
        String[] command = { "bash", "-c", "lsof -ti:8800 | xargs kill -9" };
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.inheritIO();
        pb.start();
        System.out.println("[Simulator] Sequencer crash triggered.");
    }
}