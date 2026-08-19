import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.HashMap;
import java.util.Map;

public class TLDDNSServer {
    public static void main(String[] args) {
        int port = 5002;

        // Standard Map inside main()
        Map<String, String> tldDatabase = new HashMap<>();
        tldDatabase.put("cs.umass.edu", "localhost:5003");
        tldDatabase.put("google.com", "localhost:5003");
        tldDatabase.put("annauniv.edu", "localhost:5003");

        try (DatagramSocket socket = new DatagramSocket(port)) {
            System.out.println("TLD DNS Server running on port " + port + "...");
            byte[] buffer = new byte[1024];

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String domain = new String(packet.getData(), 0, packet.getLength()).trim();
                System.out.println("[Step 4 Received] Query for: " + domain);

                String authServer = "ERROR: Authoritative Server Not Found";
                for (Map.Entry<String, String> entry : tldDatabase.entrySet()) {
                    if (domain.endsWith("."+entry.getKey())) {
                        authServer = entry.getValue();
                        break;
                    }
                }

                // Dynamically send back using socket info
                byte[] sendBuffer = authServer.getBytes();
                DatagramPacket response = new DatagramPacket(
                        sendBuffer, sendBuffer.length, packet.getAddress(), packet.getPort()
                );
                socket.send(response);
                System.out.println("[Step 5 Sent] Authoritative Server: " + authServer);
            }
        } catch (Exception e) {
            System.out.println("TLD Server Error: " + e.getMessage());
        }
    }
}