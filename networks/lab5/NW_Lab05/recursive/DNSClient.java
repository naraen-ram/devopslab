import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Scanner;

public class DNSClient {
    public static void main(String[] args)  {
        String localDnsHost = "localhost";
        int localDnsPort = 5000;

        Scanner scanner = new Scanner(System.in);

        while(true){
            try (DatagramSocket socket = new DatagramSocket()) {
    
                System.out.print("Enter domain name : ");
                String domain = scanner.nextLine().trim();

                if(domain.equalsIgnoreCase("exit")) break;
    
                InetAddress address = InetAddress.getByName(localDnsHost);
                byte[] sendData = domain.getBytes();
    
                
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, address, localDnsPort);
                socket.send(sendPacket);
                System.out.println("Querying Local DNS Server...");
    
            
                byte[] receiveBuffer = new byte[1024];
                DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                socket.receive(receivePacket);
    
                String ipAddress = new String(receivePacket.getData(), 0, receivePacket.getLength());
                System.out.println("Result IP: " + ipAddress);
    
            } catch (Exception e) {
                System.out.println("Client Error: " + e.getMessage());
            }
        }
    }
}