import java.io.*;
import java.net.*;

public class TCPChatClient {
    public static void main(String[] args) throws IOException {
        Socket socket = new Socket("localhost", 6000);

        BufferedReader serverIn = new BufferedReader(
                new InputStreamReader(socket.getInputStream()));
        PrintWriter serverOut = new PrintWriter(socket.getOutputStream(), true);
        BufferedReader userIn = new BufferedReader(
                new InputStreamReader(System.in));

        Thread listener = new Thread(() -> {
            try {
                String msg;
                while ((msg = serverIn.readLine()) != null) {
                    System.out.println(msg);
                }
            } catch (IOException e) {
                System.out.println("Disconnected from server.");
            }
        });
        listener.setDaemon(true);
        listener.start();

        String line;
        while ((line = userIn.readLine()) != null) {
            serverOut.println(line);
        }
        socket.close();
    }
}