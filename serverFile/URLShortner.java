package serverFile;

import java.io.*;
import java.net.*;

public class URLShortner {

    static final File WEB_ROOT = new File("serverFile");
    static final String FILE_NOT_FOUND = "404.html";
    static final String REDIRECT_RECORDED = "redirect_recorded.html";
    static final String DATABASE = "serverFile/database.txt";
    static final int PORT = 8080;

    public static void main(String[] args) {
        try (ServerSocket server = new ServerSocket(PORT)) {
            System.out.println("Server started on port " + PORT);

            while (true) {
                handle(server.accept());
            }

        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }

    static void handle(Socket socket) {
        try (
                socket;
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream());
                BufferedOutputStream dataOut = new BufferedOutputStream(socket.getOutputStream())) {

            String requestLine = in.readLine();
            if (requestLine == null)
                return;

            System.out.println("Request: " + requestLine);

            // =========================
            // PUT /?short=abc&long=https://example.com
            // =========================
            if (requestLine.startsWith("PUT")) {

                String[] parts = requestLine.split("[?&\\s]");
                String shortURL = getValue(parts, "short");
                String longURL = getValue(parts, "long");

                if (shortURL != null && longURL != null) {
                    save(shortURL, longURL);
                    sendFile(out, dataOut, REDIRECT_RECORDED, "200 OK");
                } else {
                    sendFile(out, dataOut, FILE_NOT_FOUND, "400 Bad Request");
                }
            }
            // =========================
            // GET /abc
            // =========================
            else if (requestLine.startsWith("GET")) {
                String shortURL = requestLine.split("\\s")[1].substring(1);
                String longURL = find(shortURL);

                if (longURL != null) {
                    File file = new File(WEB_ROOT, "redirect.html");
                    byte[] fileData;
                    try (FileInputStream fis = new FileInputStream(file)) {
                        fileData = fis.readAllBytes();
                    }
                    // REAL HTTP REDIRECT
                    out.print("HTTP/1.1 301 Moved Permanently\r\n");
                    out.print("Location: " + longURL + "\r\n");
                    out.print("Content-Type: text/html\r\n");
                    out.print("Content-Length: " + fileData.length + "\r\n");
                    out.print("\r\n");
                    out.flush();

                    dataOut.write(fileData);
                    dataOut.flush();

                } else {
                    sendFile(out, dataOut, FILE_NOT_FOUND, "404 Not Found");
                }
            }
        } catch (IOException e) {
            System.err.println("Connection error: " + e.getMessage());
        }
    }

    // =========================
    // Send HTML file
    // =========================
    static void sendFile(PrintWriter out, BufferedOutputStream dataOut,
            String filename, String status) throws IOException {

        File file = new File(WEB_ROOT, filename);
        if (!file.exists())
            return;

        byte[] data;
        try (FileInputStream fis = new FileInputStream(file)) {
            data = fis.readAllBytes();
        }

        out.print("HTTP/1.1 " + status + "\r\n");
        out.print("Content-Type: text/html\r\n");
        out.print("Content-Length: " + data.length + "\r\n");
        out.print("\r\n");
        out.flush();

        dataOut.write(data);
        dataOut.flush();
    }

    // =========================
    // Extract parameter value
    // =========================
    static String getValue(String[] parts, String key) {
        for (String p : parts) {
            if (p.startsWith(key + "=")) {
                return p.substring(key.length() + 1);
            }
        }
        return null;
    }

    // =========================
    // Find short URL in database
    // =========================
    static String find(String shortURL) throws IOException {

        File file = new File(DATABASE);
        if (!file.exists())
            return null;

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;

            while ((line = br.readLine()) != null) {
                String[] parts = line.split("\t");
                if (parts[0].equals(shortURL)) {
                    return parts[1];
                }
            }
        }

        return null;
    }

    // =========================
    // Save short → long mapping
    // =========================
    static void save(String shortURL, String longURL) throws IOException {

        try (PrintWriter pw = new PrintWriter(new FileWriter(DATABASE, true))) {
            pw.println(shortURL + "\t" + longURL);
        }
    }
}