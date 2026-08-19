import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

public class RootDNSServer {
    public static void main(String[] args) {
        int port = 5001;

        // Standard Map inside main()
        Map<String, String> rootDatabase = new HashMap<>();
        rootDatabase.put(".edu", "localhost:5002");
        rootDatabase.put(".com", "localhost:5002");
        rootDatabase.put(".org", "localhost:5002");

        try (DatagramSocket socket = new DatagramSocket(port)) {
            System.out.println("Root DNS Server running on port " + port + "...");
            byte[] buffer = new byte[1024];

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String domain = new String(packet.getData(), 0, packet.getLength()).trim();
                System.out.println("[Step 2 Received] Query for: " + domain);

                int lastDotIndex = domain.lastIndexOf('.');

                String result;
                String tldServer = "ERROR: TLD Not Found";
                if (lastDotIndex != -1) {
                    String extension = domain.substring(lastDotIndex); // e.g. ".edu" or ".com"
                    tldServer = rootDatabase.getOrDefault(extension, "ERROR: TLD Not Found");
                }

                if (tldServer.startsWith("ERROR")) {
                    result = tldServer;
                } else {
                    result = queryServer(socket, tldServer, domain, "3", "4");
                }

                // Dynamically send back using socket info
                byte[] sendBuffer = result.getBytes();
                DatagramPacket response = new DatagramPacket(
                        sendBuffer, sendBuffer.length, packet.getAddress(), packet.getPort()
                );
                socket.send(response);
                System.out.println("[Step 7 Sent] Result: " + result);
            }
        } catch (Exception e) {
            System.out.println("Root Server Error: " + e.getMessage());
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
