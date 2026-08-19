import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.HashMap;
import java.util.Map;

public class AuthoritativeDNSServer {
    public static void main(String[] args) {
        int port = 5003;

        
        Map<String, String> ipDatabase = new HashMap<>();
        ipDatabase.put("gaia.cs.umass.edu", "128.119.245.12");
        ipDatabase.put("www.google.com", "142.250.183.78");
        ipDatabase.put("www.annauniv.edu", "103.27.232.130");
        ipDatabase.put("mail.cs.umass.edu", "128.119.245.20");

        try (DatagramSocket socket = new DatagramSocket(port)) {
            System.out.println("Authoritative DNS Server running on port " + port + "...");
            byte[] buffer = new byte[1024];

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String domain = new String(packet.getData(), 0, packet.getLength()).trim();
                System.out.println("[Step 4 Received] Query for: " + domain);

                String ipAddress = ipDatabase.getOrDefault(domain, "ERROR: Domain Not Found");
                
                byte[] sendBuffer = ipAddress.getBytes();
                DatagramPacket response = new DatagramPacket(
                        sendBuffer, sendBuffer.length, packet.getAddress(), packet.getPort()
                );
                
                socket.send(response);
                System.out.println("[Step 5 Sent] IP Address: " + ipAddress);
            }
        } catch (Exception e) {
            System.out.println("Authoritative Server Error: " + e.getMessage());
        }
    }
}
