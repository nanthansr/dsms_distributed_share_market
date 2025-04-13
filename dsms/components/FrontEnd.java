
package dsms.components;

import javax.jws.WebService;
import javax.jws.WebMethod;
import javax.xml.ws.Endpoint;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@WebService
public class FrontEnd {
    private static final int SEQUENCER_PORT = 8800;
    private static final int FE_RECEIVE_PORT = 9000;
    private static final int RM_PORT = 9100;
    private static int requestCounter = 1;
    private static final Map<String, Integer> RM_PORTS = new HashMap<>();
    static {
        RM_PORTS.put("RM1", 9101);
        RM_PORTS.put("RM2", 9102);
        RM_PORTS.put("RM3", 9103);
        RM_PORTS.put("RM4", 9104);
    }

    @WebMethod
    public String invoke(String userInput) {
        if (userInput.startsWith("resetAndResyncFrom:")) {
            String wsdlUrl = userInput.split(":", 2)[1];
            return triggerReplicaResync(wsdlUrl);
        }

        int currentRequestId = requestCounter++;
        sendToSequencer(currentRequestId, userInput);
        return receiveResponses(currentRequestId);
    }

    private void sendToSequencer(int seqId, String request) {
        try (DatagramSocket socket = new DatagramSocket()) {
            String message = seqId + ":" + request;
            byte[] data = message.getBytes(StandardCharsets.UTF_8);

            InetAddress sequencerAddress = InetAddress.getByName("localhost");
            DatagramPacket packet = new DatagramPacket(data, data.length, sequencerAddress, SEQUENCER_PORT);
            socket.send(packet);
            String detectedCity = getCityFromRequest(request);
            System.out.println("[FE] Target city: " + detectedCity);
            System.out.println("[FE] Sent to Sequencer: " + message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String getCityFromRequest(String request) {
        try {
            String[] parts = request.split(":", 2); // safer split
            String method = parts[0].trim();
            String[] params = (parts.length > 1) ? parts[1].trim().split(" ") : new String[0];

            if (method.equals("listShareAvailability") || method.equals("getShares")) {
                return "ALL";
            }

            String shareID = null;

            if (method.equals("addShare") || method.equals("purchaseShare") ||
                    method.equals("sellShare") || method.equals("swapShares")) {
                if (params.length > 1) {
                    shareID = params[1];
                }
            } else if (method.equals("removeShare")) {
                if (params.length > 0) {
                    shareID = params[0];
                }
            }

            if (shareID != null && shareID.length() >= 3) {
                String prefix = shareID.substring(0, 3).toUpperCase();
                if (prefix.equals("NYK"))
                    return "NYK";
                if (prefix.equals("LON"))
                    return "LON";
                if (prefix.equals("TOK"))
                    return "TOK";
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "UNKNOWN";
    }

    private String receiveResponses(int expectedRequestId) {
        try (DatagramSocket socket = new DatagramSocket(FE_RECEIVE_PORT)) {
            socket.setSoTimeout(3000);

            Map<String, Integer> resultCountMap = new HashMap<>();
            int responsesNeeded = 2;
            int received = 0;

            while (received < 4) {
                try {
                    byte[] buffer = new byte[1024];
                    DatagramPacket responsePacket = new DatagramPacket(buffer, buffer.length);
                    socket.receive(responsePacket);

                    String response = new String(responsePacket.getData(), 0, responsePacket.getLength(),
                            StandardCharsets.UTF_8);
                    System.out.println("[FE] Received: " + response);

                    // Extract parts: expected format = seqId:result:RMx
                    String[] parts = response.split(":", 3);
                    String result = (parts.length >= 2) ? parts[1] : response;
                    String sourceReplica = (parts.length == 3) ? parts[2] : "UNKNOWN";

                    System.out.println("[FE] Result from " + sourceReplica + ": " + result);

                    // Count consensus based on result only
                    result = result.trim();
                    resultCountMap.put(result, resultCountMap.getOrDefault(result, 0) + 1);

                    if (resultCountMap.get(result) >= responsesNeeded) {
                        return "[FE] Consensus reached: " + result;
                    }

                    received++;
                } catch (SocketTimeoutException e) {
                    break; // timeout reached, break from loop
                }
            }

            // After some time or after receiving 4 results and no majority
            if (received == 4) {
                System.out.println("[FE] No consensus for request " + expectedRequestId);
                notifyRM("RM2 BUG");
            }

            return "[FE] No consensus reached";
        } catch (Exception e) {
            e.printStackTrace();
            return "[FE] Error receiving responses";
        }
    }

    private static void notifyRM(String alertMessage) {
        try (DatagramSocket socket = new DatagramSocket()) {
            byte[] data = alertMessage.getBytes(StandardCharsets.UTF_8);

            String[] parts = alertMessage.split(" ");
            String replicaId = parts[0];
            int port = RM_PORTS.containsKey(replicaId) ? RM_PORTS.get(replicaId) : 9101; // fallback if unknown

            DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getByName("localhost"), port);
            socket.send(packet);
            System.out.println("[FE] Alert sent to " + replicaId + " Manager on port " + port);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        String address = "http://localhost:9000/dsms/frontend";
        Endpoint.publish(address, new FrontEnd());
        System.out.println("[FE] SOAP service published at: " + address);
    }

    private String triggerReplicaResync(String wsdlUrl) {
        try {
            javax.xml.namespace.QName qname = new javax.xml.namespace.QName("http://dsms/", "DsmsServerService");
            javax.xml.ws.Service service = javax.xml.ws.Service.create(new java.net.URL(wsdlUrl), qname);
            dsms.DsmsServerInterface replica = service.getPort(dsms.DsmsServerInterface.class);

            // Choose an active replica (e.g. RM1) as your source
            String sourceWsdl = "http://localhost:8010/dsms/service?wsdl";

            String state = triggerStateFetch(sourceWsdl);
            replica.syncSystemState(state);

            System.out.println("[FE] Manually triggered reset and sync for replica at: " + wsdlUrl);
            return "[FE] Manual resync successful";
        } catch (Exception e) {
            e.printStackTrace();
            return "[FE] Manual resync FAILED for " + wsdlUrl;
        }
    }

    private String triggerStateFetch(String wsdlUrl) throws Exception {
        javax.xml.namespace.QName qname = new javax.xml.namespace.QName("http://dsms/", "DsmsServerService");
        javax.xml.ws.Service service = javax.xml.ws.Service.create(new java.net.URL(wsdlUrl), qname);
        dsms.DsmsServerInterface sourceReplica = service.getPort(dsms.DsmsServerInterface.class);
        return sourceReplica.getSystemState();
    }

}
