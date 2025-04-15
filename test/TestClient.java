package test;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.Scanner;

public class TestClient {
    private static final int FE_PORT = 8800;

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        System.out.println("Test Client Ready.");

        int sequenceId = new Random().nextInt(10000) + 10000;

        while (true) {
            System.out.print("Enter request (method:params) or 'exit': ");
            String input = sc.nextLine();
            if ("exit".equalsIgnoreCase(input))
                break;

            String message = sequenceId + ":" + input.trim(); // 🔒 Prefix with unique ID
            sendRequestToFrontEnd(message);
            System.out.println("[TestClient] Sent: " + sequenceId + ":" + input);
            sequenceId++; // 🔁 Always increment, even on invalid input
        }

        sc.close();
    }

    private static void sendRequestToFrontEnd(String message) {
        try (DatagramSocket socket = new DatagramSocket()) {
            byte[] data = message.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getByName("localhost"), FE_PORT);
            socket.send(packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
