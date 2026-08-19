import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class LocalDNSServer {
    public static void main(String[] args) {
        int port = 5000;
        String rootServer = "localhost:5001";

        try (DatagramSocket socket = new DatagramSocket(port)) {
            System.out.println("Local DNS Server running on port " + port + "...");
            byte[] buffer = new byte[1024];

            while (true) {
                try {
                    // Step 1: Receive request from client
                    DatagramPacket clientPacket = new DatagramPacket(buffer, buffer.length);
                    socket.receive(clientPacket);

                    String domain = new String(clientPacket.getData(), 0, clientPacket.getLength()).trim();
                    System.out.println("\n[Step 1 Received] Client query: " + domain);

                    // Steps 2 & 3: Query Root Server
                    String tldAddress = queryServer(socket, rootServer, domain, 2, 3);

                    String finalIp;
                    if (tldAddress.startsWith("ERROR")) {
                        finalIp = tldAddress;
                    } else {
                        // Steps 4 & 5: Query TLD Server
                        String authAddress = queryServer(socket, tldAddress, domain, 4, 5);

                        if (authAddress.startsWith("ERROR")) {
                            finalIp = authAddress;
                        } else {
                            // Steps 6 & 7: Query Authoritative Server
                            finalIp = queryServer(socket, authAddress, domain, 6, 7);
                        }
                    }

                    // Step 8: Send result (or error message) back to client
                    byte[] sendBuffer = finalIp.getBytes();
                    DatagramPacket clientResponse = new DatagramPacket(
                            sendBuffer, sendBuffer.length, clientPacket.getAddress(), clientPacket.getPort()
                    );
                    socket.send(clientResponse);
                    System.out.println("[Step 8 Sent] Returned result to Client: " + finalIp);

                } catch (Exception e) {
                    // Catching errors INSIDE the loop ensures the server stays alive!
                    System.out.println("Error processing query: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.out.println("Local DNS Server Socket Error: " + e.getMessage());
        }
    }

    private static String queryServer(DatagramSocket socket, String targetHostPort, String domain, int stepSend, int stepRecv) throws Exception {
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