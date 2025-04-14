package dsms.components;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import dsmsclient.FrontEndService;

import javax.xml.namespace.QName;
import javax.xml.ws.Service;
import java.net.URL;

import dsms.DsmsServerInterface;

public class ReplicaManager {
    private static final HashMap<String, String> replicaStatus = new HashMap<>();
    private static final Map<String, Long> lastHeartbeat = new HashMap<>();
    private static final int HEARTBEAT_TIMEOUT_MS = 10000;

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java ReplicaManager <ReplicaID>");
            return;
        }

        String replicaID = args[0];
        int RM_PORT = getRMPort(replicaID);
        int HEARTBEAT_PORT = getHeartbeatPort(replicaID);

        System.out.println("[ReplicaManager " + replicaID + "] Listening for alerts on port " + RM_PORT);
        System.out.println("[ReplicaManager " + replicaID + "] Listening for heartbeats on port " + HEARTBEAT_PORT);

        // Start thread for crash/bug alerts
        new Thread(() -> listenForAlerts(RM_PORT)).start();

        // Start thread for heartbeats
        new Thread(() -> listenForHeartbeats(HEARTBEAT_PORT)).start();

        // Monitor heartbeats
        new Thread(() -> monitorHeartbeats()).start();
    }

    private static void listenForAlerts(int port) {
        try (DatagramSocket socket = new DatagramSocket(port)) {
            byte[] buffer = new byte[1024];
            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String alert = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                System.out.println("[ReplicaManager] Received Alert: " + alert);

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

    private static void listenForHeartbeats(int port) {
        try (DatagramSocket socket = new DatagramSocket(port)) {
            byte[] buffer = new byte[1024];
            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String msg = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                if (msg.startsWith("HEARTBEAT:")) {
                    String replicaId = msg.split(":")[1];
                    lastHeartbeat.put(replicaId, System.currentTimeMillis());
                    System.out.println("[HEARTBEAT] Received from " + replicaId);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void monitorHeartbeats() {
        while (true) {
            try {
                Thread.sleep(5000);
                long now = System.currentTimeMillis();
                for (Map.Entry<String, Long> entry : lastHeartbeat.entrySet()) {
                    String replicaId = entry.getKey();
                    long last = entry.getValue();
                    if (now - last > HEARTBEAT_TIMEOUT_MS) {
                        System.out.println("[TIMEOUT] Missed heartbeat from " + replicaId + ". Restarting...");
                        restartReplica(replicaId);
                        lastHeartbeat.put(replicaId, now); // Prevent repeated restarts
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private static void restartReplica(String replicaId) {
        try {
            System.out.println("[ReplicaManager] Restarting " + replicaId);

            int servicePort = getServicePort(replicaId);
            ensurePortIsFree(servicePort);
            Thread.sleep(1000);

            ProcessBuilder pb = new ProcessBuilder("java", "dsms.replicas.ReplicaLauncher", replicaId);
            pb.inheritIO();
            pb.start();
            replicaStatus.put(replicaId, "RESTARTED");

            Thread.sleep(3000);

            String targetWsdl = getWsdlFromReplicaId(replicaId);
            String resyncCommand = "resetAndResyncFrom:" + targetWsdl;

            FrontEndService service = new FrontEndService();
            String result = service.getFrontEndPort().invoke(resyncCommand);
            System.out.println("[ReplicaManager] Resync result: " + result);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static int getRMPort(String replicaID) {
        switch (replicaID) {
            case "RM1": return 9101;
            case "RM2": return 9102;
            case "RM3": return 9103;
            case "RM4": return 9104;
            default: throw new IllegalArgumentException("Unknown RM ID: " + replicaID);
        }
    }

    private static int getHeartbeatPort(String replicaID) {
        switch (replicaID) {
            case "RM1": return 9991;
            case "RM2": return 9992;
            case "RM3": return 9993;
            case "RM4": return 9994;
            default: return 9999;
        }
    }

    private static int getServicePort(String replicaId) {
        switch (replicaId) {
            case "RM1": return 8010;
            case "RM2": return 8020;
            case "RM3": return 8030;
            case "RM4": return 8040;
            default: throw new IllegalArgumentException("Unknown replica: " + replicaId);
        }
    }

    private static String getWsdlFromReplicaId(String replicaId) {
        switch (replicaId) {
            case "RM1": return "http://localhost:8010/dsms/service?wsdl";
            case "RM2": return "http://localhost:8020/dsms/service?wsdl";
            case "RM3": return "http://localhost:8030/dsms/service?wsdl";
            case "RM4": return "http://localhost:8040/dsms/service?wsdl";
            default: return null;
        }
    }

    private static void ensurePortIsFree(int port) {
        try {
            new java.net.ServerSocket(port).close();
        } catch (IOException e) {
            System.out.println("[ReplicaManager] Port " + port + " is in use. Attempting to kill process...");

            try {
                ProcessBuilder pb = new ProcessBuilder("bash", "-c", "lsof -ti:" + port);
                Process p = pb.start();
                Scanner scanner = new Scanner(p.getInputStream());

                while (scanner.hasNextLine()) {
                    String pid = scanner.nextLine().trim();
                    if (!pid.isEmpty()) {
                        System.out.println("[ReplicaManager] Killing process ID: " + pid);
                        Process kill = Runtime.getRuntime().exec("kill -9 " + pid);
                        kill.waitFor();
                    }
                }
                p.waitFor();

                boolean released = false;
                for (int i = 0; i < 10; i++) {
                    Thread.sleep(500);
                    try {
                        new java.net.ServerSocket(port).close();
                        released = true;
                        Thread.sleep(1000);
                        break;
                    } catch (IOException ignore) {}
                }
                if (!released) {
                    System.out.println("[ReplicaManager] ERROR: Port " + port + " still not free after killing process.");
                }

            } catch (Exception killEx) {
                System.out.println("[ReplicaManager] Failed to kill process on port " + port);
                killEx.printStackTrace();
            }
        }
    }
}
