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

                multicastToReplicas(sequencedRequest);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void multicastToReplicas(String message) {
        try (DatagramSocket socket = new DatagramSocket()) {
            byte[] data = message.getBytes(StandardCharsets.UTF_8);
            InetAddress localhost = InetAddress.getByName("localhost");

            // Send to 4 replicas (adjust ports as needed)
            int[] ports = { 8510, 8520, 8530, 8540 };
            for (int port : ports) {
                DatagramPacket packet = new DatagramPacket(data, data.length, localhost, port);
                socket.send(packet);
                System.out.println("Sent to replica on port " + port);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}