package dsms.components;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class Sequencer {
    private static int sequenceId = 1;

    public static void main(String[] args) {
        try (DatagramSocket socket = new DatagramSocket(8800)) { // Port to receive FE requests
            System.out.println("Sequencer started on port 8800");
            byte[] buffer = new byte[1024];

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String request = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                System.out.println("Received from FE: " + request);

                String sequencedRequest = sequenceId + ":" + request;
                sequenceId++;

                String city = getCityFromRequest(request);
                System.out.println("Target city: " + city);
                multicastToReplicas(city, sequencedRequest);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void multicastToReplicas(String city, String message) {
        try (DatagramSocket socket = new DatagramSocket()) {
            byte[] data = message.getBytes(StandardCharsets.UTF_8);
            InetAddress localhost = InetAddress.getByName("localhost");

            int[] ports;
            switch (city.toUpperCase()) {
                case "NYK":
                    ports = new int[] { 8510, 8520, 8530, 8540 };
                    break;
                case "LON":
                    ports = new int[] { 8610, 8620, 8630, 8640 };
                    break;
                case "TOK":
                    ports = new int[] { 8710, 8720, 8730, 8740 };
                    break;
                case "ALL":
                    ports = new int[] {
                            8510, 8520, 8530, 8540, // NYK
                            8610, 8620, 8630, 8640, // LON
                            8710, 8720, 8730, 8740 // TOK
                    };
                    break;
                default:
                    System.out.println("Unknown city: " + city);
                    return;
            }
            for (int port : ports) {
                DatagramPacket packet = new DatagramPacket(data, data.length, localhost, port);
                socket.send(packet);
                System.out.println("Sent to replica on port " + port);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String getCityFromRequest(String request) {
        try {
            String[] parts = request.split(":");
            String method = parts[0];
            String[] params = parts.length > 1 ? parts[1].split(" ") : new String[0];

            String shareID = null;

            if (method.equals("addShare") || method.equals("purchaseShare") || method.equals("sellShare")
                    || method.equals("swapShares")) {
                if (params.length > 1) {
                    shareID = params[1];
                }
            } else if (method.equals("removeShare")) {
                if (params.length > 0) {
                    shareID = params[0];
                }
            } else if (method.equals("listShareAvailability") || method.equals("getShares")) {
                return "ALL";
            }

            // for getShares
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
            System.err.println("[ERROR] Failed to extract city: " + e.getMessage());
        }

        return "NYK"; // Final fallback
    }
}