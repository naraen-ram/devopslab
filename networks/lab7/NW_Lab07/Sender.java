

import java.io.*;
import java.net.*;

public class Sender {
    private static final String HOST = "127.0.0.1";
    private static final int PORT = 9876;
    private static final int TOTAL_PACKETS = 12;

    public static void main(String[] args) throws Exception {
        try (Socket socket = new Socket(HOST, PORT);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            System.out.println("[Client] Connected to Receiver.");

            int base = 0, nextSeqNum = 0, rwnd = 4;

            while (base < TOTAL_PACKETS) {
                // Send as many new packets as the window allows
                while (nextSeqNum - base < rwnd && nextSeqNum < TOTAL_PACKETS) {
                    System.out.printf("[Client] Sending Packet %d (In-flight: %d, rwnd: %d)%n",
                                       nextSeqNum, nextSeqNum - base + 1, rwnd);
                    out.println("DATA " + nextSeqNum++);
                    Thread.sleep(400);
                }

                if (rwnd == 0) {
                    System.out.println("[Client] rwnd = 0. Pausing and probing...");
                    Thread.sleep(1500);
                    out.println("DATA " + base); // probe with last unacked packet
                }

                String response = in.readLine();
                if (response == null || !response.startsWith("ACK")) continue;

                String[] parts = response.split(" ");
                int ackNum = Integer.parseInt(parts[1]);
                rwnd = Integer.parseInt(parts[2]);

                if (ackNum > base) {
                    base = ackNum;
                    System.out.printf("[Client] ACK: Next Expected = %d | rwnd = %d%n", ackNum, rwnd);
                } else {
                    System.out.printf("[Client] Probe ACK: Expected = %d | rwnd = %d%n", ackNum, rwnd);
                }
            }

            out.println("FIN");
            System.out.println("[Client] All packets sent and acknowledged.");
        }
    }
}