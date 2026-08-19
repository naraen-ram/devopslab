import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
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
                System.out.println("[Step 3 Received] Query for: " + domain);

                String authServer = "ERROR: Authoritative Server Not Found";
                for (Map.Entry<String, String> entry : tldDatabase.entrySet()) {
                    if (domain.endsWith("."+entry.getKey())) {
                        authServer = entry.getValue();
                        break;
                    }
                }

                String result;
                if (authServer.startsWith("ERROR")) {
                    result = authServer;
                } else {
                    result = queryServer(socket, authServer, domain, "4", "5");
                }

                // Dynamically send back using socket info
                byte[] sendBuffer = result.getBytes();
                DatagramPacket response = new DatagramPacket(
                        sendBuffer, sendBuffer.length, packet.getAddress(), packet.getPort()
                );
                socket.send(response);
                System.out.println("[Step 6 Sent] Result: " + result);
            }
        } catch (Exception e) {
            System.out.println("TLD Server Error: " + e.getMessage());
        }
    }

    private static String queryServer(DatagramSocket socket, String targetHostPort, String domain, String stepSend, String stepRecv) throws Exception {
        String[] parts = targetHostPort.split(":");
        InetAddress host = InetAddress.getByName(parts[0]);
        int targetPort = Integer.parseInt(parts[1].trim());

        byte[] sendData = domain.getBytes();
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, host, targetPort);

        System.out.println("[Step " + stepSend + " Sent] Forwarding query to " + targetHostPort);
        socket.send(sendPacket);

        byte[] recvBuffer = new byte[1024];
        DatagramPacket recvPacket = new DatagramPacket(recvBuffer, recvBuffer.length);
        socket.receive(recvPacket);

        String response = new String(recvPacket.getData(), 0, recvPacket.getLength()).trim();
        System.out.println("[Step " + stepRecv + " Received] Response: " + response);
        return response;
    }
}
