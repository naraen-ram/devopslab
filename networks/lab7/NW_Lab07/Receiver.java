
import java.io.*;
import java.net.*;

public class Receiver {
    private static final int PORT = 9876;
    private static final int BUFFER_CAPACITY = 4;

    public static void main(String[] args) throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(PORT);
             Socket socket = serverSocket.accept();
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            System.out.println("[Server] Listening on port " + PORT + "...");
            System.out.println("[Server] Sender connected.");

            ReceiveBuffer buffer = new ReceiveBuffer(BUFFER_CAPACITY);
            Thread consumerThread = new Thread(new BufferConsumer(buffer));
            consumerThread.setDaemon(true); // dies automatically when main exits, no manual stop() needed
            consumerThread.start();

            int expectedSeq = 0;
            String line;

            while ((line = in.readLine()) != null) {
                if (line.equalsIgnoreCase("FIN")) {
                    System.out.println("[Server] FIN received. Closing.");
                    break;
                }
                if (!line.startsWith("DATA")) continue;

                int seqNo = Integer.parseInt(line.split(" ")[1]);
                System.out.printf("[Server] Received Packet %d | ", seqNo);

                if (seqNo == expectedSeq && buffer.addPacket()) {
                    expectedSeq++;
                    System.out.printf("Accepted. (rwnd = %d)%n", buffer.getAvailableSpace());
                } else if (seqNo == expectedSeq) {
                    System.out.println("Buffer FULL. Dropped. (rwnd = 0)");
                } else {
                    System.out.printf("Out of order! Expected %d. (rwnd = %d)%n", expectedSeq, buffer.getAvailableSpace());
                }

                out.println("ACK " + expectedSeq + " " + buffer.getAvailableSpace());
            }
            System.out.println("[Server] Receiver closed.");
        }
    }
}

/** Thread-safe sliding receive buffer. */
class ReceiveBuffer {
    private final int capacity;
    private int used = 0;

    ReceiveBuffer(int capacity) { this.capacity = capacity; }

    synchronized boolean addPacket() {
        if (used == capacity) return false;
        used++;
        return true;
    }

    synchronized void consumePacket() {
        if (used > 0) {
            used--;
            System.out.printf("   [App Layer] Consumed 1 packet. rwnd now %d/%d%n", capacity - used, capacity);
        }
    }

    synchronized int getAvailableSpace() { return capacity - used; }
}

/** Simulates the application slowly reading data out of the receive buffer. */
class BufferConsumer implements Runnable {
    private final ReceiveBuffer buffer;

    BufferConsumer(ReceiveBuffer buffer) { this.buffer = buffer; }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Thread.sleep(2500);
                buffer.consumePacket();
            }
        } catch (InterruptedException ignored) {
            // exit quietly on shutdown
        }
    }
}