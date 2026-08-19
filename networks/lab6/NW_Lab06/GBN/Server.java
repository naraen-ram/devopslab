import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Random;

public class Server {
    private static final int PORT = 5000;
    private static final int BUFFER_SIZE = 1024;

    public static void main(String[] args) throws IOException {
        DatagramSocket socket = new DatagramSocket(PORT);
        System.out.println("=== GBN Receiver (UDP Server) Waiting on Port " + PORT + " ===");

        int expectedSeqNum = 0;
        int lastAckSent = -1;
        Random random = new Random();
        byte[] receiveData = new byte[BUFFER_SIZE];

        while (true) {
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            socket.receive(receivePacket);

            String message = new String(receivePacket.getData(), 0, receivePacket.getLength()).trim();
            if (message.equalsIgnoreCase("END")) {
                System.out.println("[SERVER] Received END signal. Shutting down server.");
                break;
            }

            InetAddress clientAddress = receivePacket.getAddress();
            int clientPort = receivePacket.getPort();

            String[] parts = message.split(":", 2);
            int seqNum = Integer.parseInt(parts[0]);
            String payload = parts[1];

            // Simulate 20% random packet loss to demonstrate Go-Back-N retransmission
            boolean simulateLoss = random.nextInt(100) < 20;

            if (simulateLoss) {
                System.out.println("[SERVER] SIMULATED PACKET LOSS: Frame " + seqNum + " (" + payload + ") dropped!");
                continue; // Discard and send no ACK
            }

            System.out.println("[SERVER] Received Frame " + seqNum + " (" + payload + ")");

            if (seqNum == expectedSeqNum) {
                System.out.println("  -> In-order frame accepted. Processing data...");
                
                sendAck(socket, clientAddress, clientPort, seqNum);
                System.out.println("  -> Sent Cumulative ACK: " + seqNum);

                lastAckSent = seqNum;
                expectedSeqNum = (expectedSeqNum + 1) % 4; // Wrap 0, 1, 2, 3
            } else {
                System.out.println("  -> Out-of-order frame! Expected " + expectedSeqNum + ", got " + seqNum + ". Discarding frame.");
                if (lastAckSent != -1) {
                    sendAck(socket, clientAddress, clientPort, lastAckSent);
                    System.out.println("  -> Re-sent Cumulative ACK for last good frame: " + lastAckSent);
                }
            }
            System.out.println();
        }

        socket.close();
        System.out.println("=== Server Closed ===");
    }

    private static void sendAck(DatagramSocket socket, InetAddress address, int port, int seqNum) throws IOException {
        String ackMessage = "ACK:" + seqNum;
        byte[] sendData = ackMessage.getBytes();
        DatagramPacket ackPacket = new DatagramPacket(sendData, sendData.length, address, port);
        socket.send(ackPacket);
    }
}