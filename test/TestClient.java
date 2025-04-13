package test;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class TestClient {
    private static final int FE_PORT = 8800;

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        System.out.println("Test Client Ready.");

        while (true) {
            System.out.print("Enter request (method:params) or 'exit': ");
            String input = sc.nextLine();
            if ("exit".equalsIgnoreCase(input))
                break;

            sendRequestToFrontEnd(input);
        }

        sc.close();
    }

    private static void sendRequestToFrontEnd(String message) {
        try (DatagramSocket socket = new DatagramSocket()) {
            // You can simulate delay or altered data here if needed
            byte[] data = message.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getByName("localhost"), FE_PORT);
            socket.send(packet);
            System.out.println("[TestClient] Sent: " + message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
