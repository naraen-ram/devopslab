import java.io.*;
import java.net.*;

public class Sender {
    static final String HOST        = "localhost";
    static final int    PORT        = 5000;
    static final int    TOTAL_PKTS  = 30;

    public static void main(String[] args) throws Exception {
        Socket socket = new Socket(HOST, PORT);
        System.out.println("[Sender] Connected to Receiver.");

        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        DataInputStream  in  = new DataInputStream(socket.getInputStream());

        int nextSeq = 0;   // next sequence number to send
        int base    = 0;   // oldest unacknowledged sequence number
        int rwnd    = 5;   // receiver's advertised window (initial)
        int unacked = 0;   // in-flight packets

        while (base < TOTAL_PKTS) {

            // --- Send phase: fill the window ---
            while (nextSeq < TOTAL_PKTS && unacked < rwnd) {
                System.out.println("[Sender] Sending pkt " + nextSeq +
                    " | unacked=" + unacked + ", rwnd=" + rwnd);
                out.writeInt(nextSeq);
                out.flush();
                nextSeq++;
                unacked++;
            }

            // --- Zero-window probe ---
            if (rwnd == 0 && nextSeq < TOTAL_PKTS) {
                System.out.println("[Sender] rwnd=0. Sending probe pkt " + nextSeq);
                Thread.sleep(2000);
                out.writeInt(nextSeq);
                out.flush();
                nextSeq++;
                unacked++;
            }

            // --- Wait for ACK from receiver ---
            int ackNum  = in.readInt();
            int newRwnd = in.readInt();

            int ackedCount = ackNum - base;
            base    = ackNum;
            unacked = Math.max(0, unacked - ackedCount);
            rwnd    = newRwnd;

            System.out.println("[Sender] ACK=" + ackNum +
                " | rwnd=" + rwnd + " | base=" + base + " | unacked=" + unacked);

            if (rwnd == 0) {
                System.out.println("[Sender] Window closed. Waiting for receiver...");
            }

            Thread.sleep(300); // pacing between rounds
        }

        System.out.println("[Sender] All " + TOTAL_PKTS + " packets acknowledged. Done.");
        socket.close();
    }
}
