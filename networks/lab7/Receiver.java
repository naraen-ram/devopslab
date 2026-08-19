import java.io.*;
import java.net.*;

public class Receiver {
    static final int PORT             = 5000;
    static final int BUFFER_CAPACITY  = 10;

    // Shared mutable buffer usage — accessed by receiver thread + consumer thread
    static int[] bufUsed = {0};

    public static void main(String[] args) throws Exception {
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("[Receiver] Listening on port " + PORT);

        Socket socket = serverSocket.accept();
        System.out.println("[Receiver] Sender connected.");

        DataInputStream  in  = new DataInputStream(socket.getInputStream());
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());

        // Consumer thread: drains 2 packets from buffer every 3 seconds
        Thread consumer = new Thread(() -> {
            while (!socket.isClosed()) {
                try {
                    Thread.sleep(3000);
                    synchronized (bufUsed) {
                        int drain = Math.min(2, bufUsed[0]);
                        if (drain > 0) {
                            bufUsed[0] -= drain;
                            System.out.println("[Receiver] App consumed " + drain +
                                " pkts | Buffer: " + bufUsed[0] + "/" + BUFFER_CAPACITY);
                        }
                    }
                } catch (InterruptedException e) { break; }
            }
        });
        consumer.setDaemon(true);
        consumer.start();

        int expectedSeq = 0;

        while (true) {
            try {
                int seqNum = in.readInt();

                synchronized (bufUsed) {
                    if (bufUsed[0] < BUFFER_CAPACITY) {
                        bufUsed[0]++;
                        expectedSeq = seqNum + 1;
                        System.out.println("[Receiver] Rcvd pkt " + seqNum +
                            " | Buffer: " + bufUsed[0] + "/" + BUFFER_CAPACITY);
                    } else {
                        // Buffer full: drop packet, re-send same ACK & rwnd=0
                        System.out.println("[Receiver] Buffer FULL. Dropping pkt " + seqNum);
                    }

                    int rwnd = BUFFER_CAPACITY - bufUsed[0];
                    out.writeInt(expectedSeq);
                    out.writeInt(rwnd);
                    out.flush();
                    System.out.println("[Receiver] ACK=" + expectedSeq + ", rwnd=" + rwnd);
                }
            } catch (EOFException e) {
                System.out.println("[Receiver] Sender disconnected.");
                break;
            }
        }

        socket.close();
        serverSocket.close();
    }
}
