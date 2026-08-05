import java.io.*;
import java.net.*;

public class TCPFileClient {
    public static void main(String[] args) throws IOException {
        Socket socket = new Socket("localhost", 8080);
        BufferedReader serverIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        PrintWriter serverOut = new PrintWriter(socket.getOutputStream(), true);
        BufferedReader userIn = new BufferedReader(new InputStreamReader(System.in));

        String cookieId = null; // client's local "cookie jar" - empty until server assigns one

        while (true) {
            System.out.print("Enter filename to request (or 'exit' to quit): ");
            String filename = userIn.readLine();

            if (filename == null || filename.equalsIgnoreCase("exit")) {
                System.out.println("Disconnecting from server...");
                break; // just closes socket below, no request sent
            }

            // Build and send the request
            serverOut.println("GET /" + filename + " HTTP/1.1");
            if (cookieId != null) {
                serverOut.println("Cookie: id=" + cookieId);
            }
            serverOut.println(); // blank line ends the request

            // Read the response until blank line
            String line;
            while ((line = serverIn.readLine()) != null) {
                if (line.startsWith("Set-Cookie:")) {
                    String value = line.substring("Set-Cookie:".length()).trim();
                    if (value.startsWith("id=")) {
                        cookieId = value.substring(3); // store cookie for future requests
                    }
                }

                System.out.println(line);

                if (line.isEmpty()) {
                    break; // end of this response
                }
            }
            System.out.println("----");
        }

        socket.close();
    }
}