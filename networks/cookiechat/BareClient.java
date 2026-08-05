import java.io.*;
import java.net.*;

public class BareClient {
    public static void main(String[] args) throws Exception {
        System.out.println("--- Request 1 (No Cookie Sent) ---");
        request("file1.txt", null);

        System.out.println("\n--- Request 2 (Sending Cookie ID_1) ---");
        request("file2.txt", "ID_1");
    }

    static void request(String file, String cookie) throws Exception {
        Socket socket = new Socket("localhost", 8080);
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        // Send: "filename" OR "filename cookie"
        out.println(file + (cookie != null ? " " + cookie : ""));

        // Print all lines returned by the server
        in.lines().forEach(System.out::println);
        socket.close();
    }
}