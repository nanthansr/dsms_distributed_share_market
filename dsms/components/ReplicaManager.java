package dsms.components;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

public class ReplicaManager {
    private static final int RM_PORT = 9100; // Port to listen for FE alerts
    private static final HashMap<String, String> replicaStatus = new HashMap<>();

    public static void main(String[] args) {
        System.out.println("[ReplicaManager] Listening for failure alerts on port " + RM_PORT);
        try (DatagramSocket socket = new DatagramSocket(RM_PORT)) {
            byte[] buffer = new byte[1024];

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String alert = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                System.out.println("[ReplicaManager] Received Alert: " + alert);

                // Format: "RM2 BUG" or "RM3 CRASH"
                String[] parts = alert.split(" ");
                String replicaId = parts[0];
                String issue = parts[1];

                if ("BUG".equals(issue) || "CRASH".equals(issue)) {
                    restartReplica(replicaId);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void restartReplica(String replicaId) {
        try {
            System.out.println("[ReplicaManager] Restarting " + replicaId);

            // Sample: java ReplicaLauncher NY RM2
            // You can change these as needed depending on mapping
            String city = getCityFromReplica(replicaId);
            ProcessBuilder pb = new ProcessBuilder("java", "dsms.replicas.ReplicaLauncher", city, replicaId);
            pb.inheritIO(); // Optional: redirect output to console
            pb.start();

            replicaStatus.put(replicaId, "RESTARTED");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String getCityFromReplica(String replicaId) {
        // You could improve this to use a config or mapping table
        switch (replicaId) {
            case "RM1":
                return "NY";
            case "RM2":
                return "LON";
            case "RM3":
                return "TOK";
            case "RM4":
                return "NY"; // Assume second NY replica for simplicity
            default:
                return "NY";
        }
    }
}
