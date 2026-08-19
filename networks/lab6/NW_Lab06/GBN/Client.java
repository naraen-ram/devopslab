import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;

public class Client {
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 5000;
    private static final int BUFFER_SIZE = 1024;

    public static void main(String[] args) throws IOException {
        DatagramSocket socket = new DatagramSocket();
        socket.setSoTimeout(2500); // 2.5-second timer for unACKed packets
        InetAddress serverAddress = InetAddress.getByName(SERVER_HOST);

        int windowSize = 4;
        int totalPackets = 10;

        int base = 0;       // Index of oldest unacknowledged packet
        int nextSeqNum = 0; // Index of next packet to send

        System.out.println("=== GBN Sender (UDP Client) Started (Window Size N = " + windowSize + ") ===\n");

        while (base < totalPackets) {
            // Send up to N unacknowledged packets
            while (nextSeqNum < base + windowSize && nextSeqNum < totalPackets) {
                int seqNum = nextSeqNum % 4; // Sequence space 0, 1, 2, 3
                String payload = "Data_" + nextSeqNum;
                String message = seqNum + ":" + payload;

                byte[] sendData = message.getBytes();
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, SERVER_PORT);
                socket.send(sendPacket);

                System.out.println("[CLIENT] Sent Packet " + seqNum + " [Global Index: " + nextSeqNum + "]");
                nextSeqNum++;
            }

            // Listen for ACKs
            try {
                byte[] receiveData = new byte[BUFFER_SIZE];
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                socket.receive(receivePacket);

                String response = new String(receivePacket.getData(), 0, receivePacket.getLength()).trim();

                if (response.startsWith("ACK:")) {
                    int ackSeq = Integer.parseInt(response.split(":")[1]);
                    System.out.println("  [CLIENT] Received ACK: " + ackSeq);

                    // Process cumulative ACK: advance base for all frames up to ackSeq
                    for (int i = base; i < nextSeqNum; i++) {
                        if (i % 4 == ackSeq) {
                            base = i + 1; // Slide window past the ACKed packet
                            System.out.println("  [CLIENT] Window slid forward! New base index: " + base + " (Seq " + (base % 4) + ")");
                            break;
                        }
                    }
                }
            } catch (SocketTimeoutException e) {
                System.out.println("\n[CLIENT] *** TIMER EXPIRED for Base Index " + base + " (Seq " + (base % 4) + ") ***");
                System.out.println("[CLIENT] Go-Back-N: Retransmitting frames from index " + base + " to " + (nextSeqNum - 1) + "...\n");

                // Go Back N: Reset nextSeqNum back to base to force retransmission
                nextSeqNum = base;
            }
            System.out.println();
        }

        // Send END packet to notify server
        byte[] endData = "END".getBytes();
        DatagramPacket endPacket = new DatagramPacket(endData, endData.length, serverAddress, SERVER_PORT);
        socket.send(endPacket);

        socket.close();
        System.out.println("=== All " + totalPackets + " packets successfully delivered and ACKed ===");
    }
}