package dsms.components;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class FrontEnd {
    private static final int SEQUENCER_PORT = 8800;
    private static final int FE_RECEIVE_PORT = 9000;
    private static final int RM_PORT = 9100;
    private static int requestCounter = 1;

    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);
        System.out.println("Front-End Started. Type request in format: method:params");
        new Thread(() -> receiveResponses()).start();

        while (true) {
            System.out.print("Request: ");
            String userInput = sc.nextLine(); // Example: listShareAvailability:NYKA1000
            sendToSequencer(requestCounter++, userInput);
        }
    }

    private static void sendToSequencer(int seqId, String request) {
        try (DatagramSocket socket = new DatagramSocket()) {
            String message = request;
            byte[] data = message.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getByName("localhost"),
                    SEQUENCER_PORT);
            socket.send(packet);
            System.out.println("[FE] Sent to Sequencer: " + message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void receiveResponses() {
        try (DatagramSocket socket = new DatagramSocket(FE_RECEIVE_PORT)) {
            Map<Integer, List<String>> responseMap = new HashMap<>();
            Map<Integer, Map<String, Integer>> resultCountMap = new HashMap<>();
            byte[] buffer = new byte[1024];

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String response = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                System.out.println("[FE] Received: " + response);

                String[] parts = response.split(":", 2);
                int seqId = Integer.parseInt(parts[0]);
                String result = parts[1];

                responseMap.putIfAbsent(seqId, new ArrayList<>());
                resultCountMap.putIfAbsent(seqId, new HashMap<>());

                responseMap.get(seqId).add(result);
                resultCountMap.get(seqId).put(result, resultCountMap.get(seqId).getOrDefault(result, 0) + 1);

                // Wait for 3 matching results
                for (Map.Entry<String, Integer> entry : resultCountMap.get(seqId).entrySet()) {
                    if (entry.getValue() >= 2) {
                        System.out.println("[FE] Majority Result for " + seqId + ": " + entry.getKey());
                        return;
                    }
                }

                // After some time or after receiving 4 results and no majority
                if (responseMap.get(seqId).size() == 4) {
                    System.out.println("[FE] No consensus for request " + seqId);
                    notifyRM("RM2 BUG"); // Placeholder, you'd map this based on mismatch
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void notifyRM(String alertMessage) {
        try (DatagramSocket socket = new DatagramSocket()) {
            byte[] data = alertMessage.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getByName("localhost"), RM_PORT);
            socket.send(packet);
            System.out.println("[FE] Alert sent to RM: " + alertMessage);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
