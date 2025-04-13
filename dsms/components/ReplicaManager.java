package dsms.components;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Scanner;

import javax.xml.namespace.QName;
import javax.xml.ws.Service;
import java.net.URL;

import dsms.DsmsServerInterface;

public class ReplicaManager {
    private static final HashMap<String, String> replicaStatus = new HashMap<>();

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java ReplicaManager <ReplicaID>");
            return;
        }

        String replicaID = args[0];
        int RM_PORT;

        switch (replicaID) {
            case "RM1":
                RM_PORT = 9101;
                break;
            case "RM2":
                RM_PORT = 9102;
                break;
            case "RM3":
                RM_PORT = 9103;
                break;
            case "RM4":
                RM_PORT = 9104;
                break;
            default:
                throw new IllegalArgumentException("Unknown RM ID: " + replicaID);
        }

        System.out.println("[ReplicaManager " + replicaID + "] Listening for failure alerts on port " + RM_PORT);
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

            int servicePort = getServicePort(replicaId);
            ensurePortIsFree(servicePort);

            // ⏳ Short wait to make sure the port is fully released
            Thread.sleep(1000);

            ProcessBuilder pb = new ProcessBuilder("java", "dsms.replicas.ReplicaLauncher", replicaId);
            pb.inheritIO();
            pb.start();

            replicaStatus.put(replicaId, "RESTARTED");

            // 💤 Let the replica launch before resyncing
            Thread.sleep(3000);

            // Trigger manual resync via FrontEnd
            String targetWsdl = getWsdlFromReplicaId(replicaId);
            String resyncCommand = "resetAndResyncFrom:" + targetWsdl;

            System.out.println("[ReplicaManager] Triggering auto-resync: " + resyncCommand);

            dsmsclient.FrontEndService service = new dsmsclient.FrontEndService();
            dsmsclient.FrontEnd fe = service.getFrontEndPort();
            String result = fe.invoke(resyncCommand);
            System.out.println("[ReplicaManager] Resync result: " + result);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static int getServicePort(String replicaId) {
        switch (replicaId) {
            case "RM1":
                return 8010;
            case "RM2":
                return 8020;
            case "RM3":
                return 8030;
            case "RM4":
                return 8040;
            default:
                throw new IllegalArgumentException("Unknown replica: " + replicaId);
        }
    }

    private static String getWsdlFromReplicaId(String replicaId) {
        switch (replicaId) {
            case "RM1":
                return "http://localhost:8010/dsms/service?wsdl";
            case "RM2":
                return "http://localhost:8020/dsms/service?wsdl";
            case "RM3":
                return "http://localhost:8030/dsms/service?wsdl";
            case "RM4":
                return "http://localhost:8040/dsms/service?wsdl";
            default:
                return null;
        }
    }

    private static void ensurePortIsFree(int port) {
        try {
            // Check if port is free
            new java.net.ServerSocket(port).close();
        } catch (IOException e) {
            System.out.println("[ReplicaManager] Port " + port + " is in use. Attempting to kill process...");

            try {
                // Kill process using the port (macOS/Linux)
                ProcessBuilder pb = new ProcessBuilder("bash", "-c", "lsof -ti:" + port);
                Process p = pb.start();

                Scanner scanner = new Scanner(p.getInputStream());
                while (scanner.hasNextLine()) {
                    String pid = scanner.nextLine().trim();
                    if (!pid.isEmpty()) {
                        System.out.println("[ReplicaManager] Killing process ID: " + pid);
                        Runtime.getRuntime().exec("kill -9 " + pid);
                    }
                }
                p.waitFor();

                // 🔁 Confirm the port is now free
                boolean released = false;
                for (int i = 0; i < 10; i++) { // Try for up to 5 seconds
                    Thread.sleep(500);
                    try {
                        new java.net.ServerSocket(port).close();
                        released = true;
                        break;
                    } catch (IOException ignore) {
                    }
                }

                if (!released) {
                    System.out
                            .println("[ReplicaManager] ERROR: Port " + port + " still not free after killing process.");
                }

            } catch (Exception killEx) {
                System.out.println("[ReplicaManager] Failed to kill process on port " + port);
                killEx.printStackTrace();
            }
        }
    }

}
