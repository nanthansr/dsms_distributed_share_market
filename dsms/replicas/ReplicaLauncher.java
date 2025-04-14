package dsms.replicas;

import dsms.DsmsServer;
import dsms.DsmsServerInterface;

import javax.xml.ws.Endpoint;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.net.URL;
import javax.xml.namespace.QName;
import javax.xml.ws.Service;

public class ReplicaLauncher {
    private static final ConcurrentHashMap<Integer, String> requestLog = new ConcurrentHashMap<>();
    private static final int HEARTBEAT_INTERVAL_MS = 5000;

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java ReplicaLauncher <ReplicaID>");
            return;
        }

        String replicaId = args[0];
        System.setProperty("replicaId", replicaId);
        int udpPort = getUDPPort(replicaId);

        // Start Web Service
        String serviceURL = "http://localhost:" + getServicePort(replicaId) + "/dsms/service";

        DsmsServer realServer = new DsmsServer(replicaId);
        DsmsServerInterface serverImpl = realServer;

        Endpoint.publish(serviceURL, serverImpl);

        // Sync logic (unchanged)
        List<String> replicaWsdlList = new ArrayList<>();
        if (!replicaId.equalsIgnoreCase("RM1")) replicaWsdlList.add("http://localhost:8010/dsms/service?wsdl");
        if (!replicaId.equalsIgnoreCase("RM2")) replicaWsdlList.add("http://localhost:8020/dsms/service?wsdl");
        if (!replicaId.equalsIgnoreCase("RM3")) replicaWsdlList.add("http://localhost:8030/dsms/service?wsdl");
        if (!replicaId.equalsIgnoreCase("RM4")) replicaWsdlList.add("http://localhost:8040/dsms/service?wsdl");

        boolean synced = false;
        for (String wsdl : replicaWsdlList) {
            try {
                System.out.println("[SYNC] Dynamically searching for active replicas to sync state ");
                QName qname = new QName("http://dsms/", "DsmsServerService");
                Service syncService = Service.create(new URL(wsdl), qname);
                DsmsServerInterface source = syncService.getPort(DsmsServerInterface.class);
                String encoded = source.getSystemState();
                serverImpl.syncSystemState(encoded);
                System.out.println("[SYNC] Replica " + replicaId + " just synced from: " + wsdl);
                synced = true;
                break;
            } catch (Exception e) {
                System.out.println("[SYNC] Failed to sync from: " + wsdl);
            }
        }

        if (!synced) {
            System.out.println("[SYNC INFO] No available source to sync from. Starting fresh.");
        }

        System.out.println("Replica " + replicaId + " launched at " + serviceURL + " — manages NYK, LON, TOK");

        new Thread(() -> listenUDP(realServer, udpPort, replicaId)).start();
        new Thread(() -> sendHeartbeats(replicaId)).start();
    }

    private static void listenUDP(DsmsServerInterface serverImpl, int port, String replicaId) {
        try (DatagramSocket socket = new DatagramSocket(port)) {
            System.out.println("Listening for sequencer requests on UDP port " + port);
            byte[] buffer = new byte[1024];

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                System.out.println("[Replica] Received: " + message);

                String[] parts = message.split(":", 3);
                int seqId = Integer.parseInt(parts[0]);
                String method = parts[1];
                String params = parts.length > 2 ? parts[2] : "";

                DsmsServer realServer = (DsmsServer) serverImpl;

                if (!realServer.hasProcessed(seqId)) {
                    try {
                        System.out.println("[DEBUG] method=" + method + ", params=" + params);
                        String result = invokeMethod(serverImpl, method, params);
                        requestLog.put(seqId, result);
                        realServer.markProcessed(seqId);
                        System.out.println("[DEBUG] Marked seqId " + seqId + " as processed.");
                        System.out.println("[DEBUG] hasProcessed(" + seqId + ")? " + realServer.hasProcessed(seqId));
                        sendResponseToFE(seqId, result, replicaId);
                    } catch (Exception e) {
                        e.printStackTrace();
                        sendCrashAlertToRM(replicaId);
                        break;
                    }
                } else {
                    String cachedResult = requestLog.getOrDefault(seqId, "Result unavailable");
                    System.out.println("[Replica] Request " + seqId + " already processed. Re-sending cached result.");
                    sendResponseToFE(seqId, cachedResult, replicaId);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void sendResponseToFE(int seqId, String result, String replicaId) {
        try (DatagramSocket socket = new DatagramSocket()) {
            String response = seqId + ":" + result + "::" + replicaId;
            System.out.println("[DEBUG] Final response to FE: " + response);
            byte[] data = response.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getByName("localhost"), 9000);
            socket.send(packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String invokeMethod(DsmsServerInterface server, String method, String params) {
        System.out.println("[DEBUG] Method received: " + method);
        String[] args = params.split(" ");
        switch (method) {
            case "resetAndResyncFrom": return server.resetAndResyncFrom(args[0]);
            case "addShare": return server.addShare(args[0], args[1], Integer.parseInt(args[2]));
            case "removeShare": return server.removeShare(args[0], args[1]);
            case "listShareAvailability": return server.listShareAvailability(args[0]);
            case "getShares": return server.getShares(args[0]);
            case "sellShare": return server.sellShare(args[0], args[1], Integer.parseInt(args[2]));
            case "purchaseShare": return server.purchaseShare(args[0], args[1], args[2], Integer.parseInt(args[3]));
            case "swapShares": return server.swapShares(args[0], args[1], args[2], args[3], args[4]);
            case "cancelReservation": return server.cancelReservation(args[0], args[1], Integer.parseInt(args[2]));
            default: return "NOT_IMPLEMENTED";
        }
    }

    private static void sendCrashAlertToRM(String replicaId) {
        try (DatagramSocket socket = new DatagramSocket()) {
            String message = replicaId + " CRASH";
            byte[] buffer = message.getBytes(StandardCharsets.UTF_8);
            InetAddress rmAddress = InetAddress.getByName("localhost");
            int rmPort = getRMPort(replicaId);
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, rmAddress, rmPort);
            socket.send(packet);
            System.out.println("[ALERT] Sent crash alert for " + replicaId + " to ReplicaManager.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void sendHeartbeats(String replicaId) {
        try (DatagramSocket socket = new DatagramSocket()) {
            InetAddress address = InetAddress.getByName("localhost");
            int port = getHeartbeatPort(replicaId);
            while (true) {
                String heartbeat = "HEARTBEAT:" + replicaId;
                byte[] data = heartbeat.getBytes(StandardCharsets.UTF_8);
                DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
                socket.send(packet);
                Thread.sleep(HEARTBEAT_INTERVAL_MS);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static int getUDPPort(String replicaId) {
        switch (replicaId.toUpperCase()) {
            case "RM1": return 8510;
            case "RM2": return 8520;
            case "RM3": return 8530;
            case "RM4": return 8540;
            default: return 8550;
        }
    }

    private static int getServicePort(String replicaId) {
        switch (replicaId.toUpperCase()) {
            case "RM1": return 8010;
            case "RM2": return 8020;
            case "RM3": return 8030;
            case "RM4": return 8040;
            default: return 8050;
        }
    }

    private static int getRMPort(String replicaId) {
        switch (replicaId.toUpperCase()) {
            case "RM1": return 9101;
            case "RM2": return 9102;
            case "RM3": return 9103;
            case "RM4": return 9104;
            default: return 9110;
        }
    }

    private static int getHeartbeatPort(String replicaId) {
        switch (replicaId.toUpperCase()) {
            case "RM1": return 9991;
            case "RM2": return 9992;
            case "RM3": return 9993;
            case "RM4": return 9994;
            default: return 9999;
        }
    }
}
