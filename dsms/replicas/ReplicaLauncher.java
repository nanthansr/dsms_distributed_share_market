package dsms.replicas;

import dsms.DsmsServer;
import dsms.DsmsServerInterface;

import javax.xml.ws.Endpoint;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.net.URL;
import javax.xml.namespace.QName;
import javax.xml.ws.Service;

import dsms.DsmsServerInterface;


public class ReplicaLauncher {
    private static final ConcurrentHashMap<Integer, String> requestLog = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java ReplicaLauncher <City> <ReplicaID>");
            return;
        }

        String city = args[0]; // NY, LON, TOK, etc.
        String replicaId = args[1]; // RM1, RM2, etc.
        int udpPort = getUDPPort(city, replicaId);

        // Start Web Service
        String serviceURL = "http://localhost:" + getServicePort(city, replicaId) + "/dsms/" + city.toLowerCase();
        DsmsServerInterface serverImpl = new DsmsServer(city);
        Endpoint.publish(serviceURL, serverImpl);
        try {
            String sourceWsdl = "http://localhost:8010/dsms/ny?wsdl";
            QName qname = new QName("http://dsms/", "DsmsServerService");
            Service syncService = Service.create(new URL(sourceWsdl), qname);
            DsmsServerInterface source = syncService.getPort(DsmsServerInterface.class);
            String encoded = source.getSystemState();
            serverImpl.syncSystemState(encoded);
            System.out.println("[SYNC] Successfully synced state from " + sourceWsdl);
        } catch (Exception e) {
            System.out.println("[SYNC INFO] No available source to sync from. Starting fresh.");
        }

        System.out.println("Replica for " + city + " launched at " + serviceURL);

        // Start listening to UDP from Sequencer
        new Thread(() -> listenUDP(serverImpl, udpPort)).start();
    }

    private static void listenUDP(DsmsServerInterface serverImpl, int port) {
        try (DatagramSocket socket = new DatagramSocket(port)) {
            System.out.println("Listening for sequencer requests on UDP port " + port);
            byte[] buffer = new byte[1024];

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                System.out.println("[Replica] Received: " + message);

                // Parse message format: "seqId:method:params"
                String[] parts = message.split(":", 3);
                int seqId = Integer.parseInt(parts[0]);
                String method = parts[1];
                String params = parts.length > 2 ? parts[2] : "";

                // Ensure total order execution
                if (!requestLog.containsKey(seqId)) {
                    System.out.println("[DEBUG] method=" + method + ", params=" + params);
                    String result = invokeMethod(serverImpl, method, params);
                    requestLog.put(seqId, result);

                    // Send back to FE (placeholder)
                    sendResponseToFE(seqId, result);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String invokeMethod(DsmsServerInterface server, String method, String params) {
        System.out.println("[DEBUG] Method received: " + method); // Add this line

        String[] args = params.split(" ");
        switch (method) {
            case "addShare":
                return server.addShare(args[0], args[1], Integer.parseInt(args[2]));

            case "removeShare":
                return server.removeShare(args[0], args[1]);

            case "listShareAvailability":
                return server.listShareAvailability(args[0]);

            case "getShares":
                return server.getShares(args[0]);

            case "sellShare":
                return server.sellShare(args[0], args[1], Integer.parseInt(args[2]));

            case "purchaseShare":
                return server.purchaseShare(args[0], args[1], args[2], Integer.parseInt(args[3]));

            case "swapShares":
                return server.swapShares(args[0], args[1], args[2], args[3], args[4]);

            default:
                return "NOT_IMPLEMENTED";
        }
    }

    private static void sendResponseToFE(int seqId, String result) {
        try (DatagramSocket socket = new DatagramSocket()) {
            String response = seqId + ":" + result;
            byte[] data = response.getBytes(StandardCharsets.UTF_8);
            InetAddress feAddress = InetAddress.getByName("localhost");
            DatagramPacket packet = new DatagramPacket(data, data.length, feAddress, 9000);
            socket.send(packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static int getServicePort(String city, String replicaId) {
        int basePort;
        switch (city.toUpperCase()) {
            case "NY":
                basePort = 8000;
                break;
            case "LON":
                basePort = 8100;
                break;
            case "TOK":
                basePort = 8200;
                break;
            case "NYK":
                basePort = 8000;
                break;
            default:
                basePort = 8300;
                break;
        }
        int offset = Integer.parseInt(replicaId.substring(2)) * 10;
        return basePort + offset;
    }

    private static int getUDPPort(String city, String replicaId) {
        int basePort;
        switch (city.toUpperCase()) {
            case "NY":
                basePort = 8500;
                break;
            case "LON":
                basePort = 8600;
                break;
            case "TOK":
                basePort = 8700;
                break;
            default:
                basePort = 8800;
                break;
        }
        int offset = Integer.parseInt(replicaId.substring(2)) * 10;
        return basePort + offset;
    }
}