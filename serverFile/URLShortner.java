package serverFile;

import java.io.*;
import java.net.*;

public class URLShortner {
    static final File WEB_ROOT = new File("serverFile");
    static final String FILE_NOT_FOUND = "404.html";
    static final String REDIRECT_RECORDED = "redirect_recorded.html";
    static final String REDIRECT = "redirect.html";
    static final String DATABASE = "serverFile/database.txt";
    static final int PORT = 8080;

    public static void main(String[] args) throws IOException {
        try (ServerSocket server = new ServerSocket(PORT)) {
            System.out.println("Listening on port " + PORT);
            while (true) handle(server.accept());
        }
    }

    static void handle(Socket socket) throws IOException {
        try (
            socket;
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream());
            BufferedOutputStream dataOut = new BufferedOutputStream(socket.getOutputStream())
        ) {
            String request = in.readLine();
            if (request == null) return;

            // PUT /?short=X&long=Y
            if (request.startsWith("PUT")) {
                String[] params = request.split("[?&\\s]");
                save(getValue(params, "short"), getValue(params, "long"));
                sendFile(out, dataOut, REDIRECT_RECORDED, "200 OK");

            // GET /shortcode
            } else if (request.startsWith("GET")) {
                String shortURL = request.split("\\s")[1].substring(1);
                String longURL = find(shortURL);
                if (longURL != null) {
                    sendFile(out, dataOut, REDIRECT, "200 OK");
                } else {
                    sendFile(out, dataOut, FILE_NOT_FOUND, "404 Not Found");
                }
            }
        }
    }

    static void sendFile(PrintWriter out, BufferedOutputStream dataOut, String filename, String status) throws IOException {
        File file = new File(WEB_ROOT, filename);
        byte[] data;
        try (FileInputStream fis = new FileInputStream(file)) { data = fis.readAllBytes(); }
        out.print("HTTP/1.1 " + status + "\r\nContent-Type: text/html\r\nContent-Length: " + data.length + "\r\n\r\n");
        out.flush();
        dataOut.write(data);
        dataOut.flush();
    }

    static String getValue(String[] parts, String key) {
        for (String p : parts)
            if (p.startsWith(key + "=")) return p.substring(key.length() + 1);
        return null;
    }

    // GET
    static String find(String shortURL) throws IOException {
        File f = new File(DATABASE);
        if (!f.exists()) return null;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("\t");
                if (parts[0].equals(shortURL)) return parts[1];
            }
        }
        return null;
    }

    // PUT
    static void save(String shortURL, String longURL) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(DATABASE, true))) {
            pw.println(shortURL + "\t" + longURL);
        }
    }
}