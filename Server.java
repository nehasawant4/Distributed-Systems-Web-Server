import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.net.SocketException;


public class Server {
    private static String DOCUMENT_ROOT = null;
    private static int PORT;

    public static void main(String[] args) {
        setupServer(args);
        try {
            startServer();
        } catch (IOException e) {
            System.err.println("Failed to start the server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void setupServer(String[] args) {
        if (args.length == 4) {
            DOCUMENT_ROOT = args[1];
            PORT = Integer.parseInt(args[3]);
        } else {
            System.err.println("Usage: java Server -document_root <path> -port <port>");
            System.exit(1);
        }
    }

    private static void startServer() throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("Server listening on port: " + PORT);

        while (true) {
            Socket clientSocket = serverSocket.accept();
            System.out.println("Connection established");
            handleClientConnection(clientSocket);
        }
    }

    private static void handleClientConnection(Socket clientSocket) {
        new Thread(new ConnectionHandler(clientSocket), "Thread-" + clientSocket.hashCode()).start();
    }

    private static class ConnectionHandler implements Runnable {
        private Socket clientSocket;

        public ConnectionHandler(Socket clientSocket) {
            setClientSocket(clientSocket);
        }

        private void setClientSocket(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        @Override
        public void run() {
            handleRequest();
        }

        private void handleRequest() {
            parseRequest(this.clientSocket);
        }
    }

    private static void parseRequest(Socket clientSocket) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             OutputStream outputStream = clientSocket.getOutputStream()) {
            processClientRequest(reader, outputStream, clientSocket);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void processClientRequest(BufferedReader reader, OutputStream outputStream, Socket clientSocket) throws IOException {
        String requestLine = reader.readLine();
        System.out.println("\nReceived request: " + requestLine);

        if (!isValidGetRequest(requestLine)) {
            sendErrorResponse(outputStream, "Error Code: 400 Bad Request", " ");
            return;
        }

        String[] requestParts = requestLine.split(" ");
        String requestHttpVersion = requestParts[2];
        String requestFileName = getRequestFileName(requestParts[1]);

        String filePath = DOCUMENT_ROOT + requestFileName;
        File file = new File(filePath);

        if (!isValidFile(file, filePath)) {
            sendErrorResponse(outputStream, "404 File Not Found", requestHttpVersion);
            return;
        }

        if (!file.canRead()) {
            sendErrorResponse(outputStream, "403 Forbidden", requestHttpVersion);
            return;
        }

        handleHttpRequestVersion(outputStream, filePath, clientSocket, requestHttpVersion);
    }

    private static boolean isValidGetRequest(String requestLine) {
        return requestLine != null && requestLine.startsWith("GET");
    }

    private static String getRequestFileName(String requestFileName) {
        if (requestFileName.equals("/") || requestFileName.isEmpty()) {
            return "/index.html";
        }
        return requestFileName;
    }

    private static boolean isValidFile(File file, String filePath) {
        return file.exists() && !Files.isDirectory(Paths.get(filePath));
    }

    private static void handleHttpRequestVersion(OutputStream outputStream, String filePath, Socket clientSocket, String httpVersion) throws IOException {
        if (httpVersion.endsWith("1.0")) {
            handleHttpRequest1_0(outputStream, filePath, clientSocket);
        } else {
            handleHttpRequest1_1(outputStream, filePath, clientSocket);
        }
    }



    private static void handleHttpRequest1_0(OutputStream outputStream, String filePath, Socket clientSocket) throws IOException {
        clientSocket.setKeepAlive(false);
        sendHttpResponse(outputStream, filePath);
        closeClientConnection(clientSocket);
    }

    private static void sendHttpResponse(OutputStream outputStream, String filePath) throws IOException {
        File file = new File(filePath);
        String contentType = getContentType(filePath);
        String charset = getCharset(contentType);
        String responseHeader = buildResponseHeader(contentType, charset, file);

        outputStream.write(responseHeader.getBytes());
        sendFileContent(outputStream, file);
        outputStream.flush();
    }

    private static String getCharset(String contentType) {
        return contentType.startsWith("text/") ? "; charset=UTF-8" : "";
    }

    private static String buildResponseHeader(String contentType, String charset, File file) {
        return "HTTP/1.0 200 OK\r\n" +
                "Content-Type: " + contentType + charset + "\r\n" +
                "Content-Length: " + file.length() + "\r\n" +
                "Date: " + getCurrentDate() + "\r\n" +
                "\r\n";
    }

    private static void sendFileContent(OutputStream outputStream, File file) throws IOException {
        try (BufferedInputStream fileInputStream = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }
    }

    private static void closeClientConnection(Socket clientSocket) throws IOException {
        clientSocket.close();
        System.out.println("\nHTTP 1.0: Client Socket closed");
    }



    private static void handleHttpRequest1_1(OutputStream outputStream, String filePath, Socket clientSocket) throws IOException {
        configureConnectionForKeepAlive(clientSocket);
        writeHttpResponse(outputStream, filePath);
        flushAndHandleTimeout(outputStream, clientSocket);
    }

    private static void configureConnectionForKeepAlive(Socket clientSocket) throws SocketException {
        int keepAliveTimeout = 60000; // in milliseconds
        clientSocket.setKeepAlive(true);
        clientSocket.setSoTimeout(keepAliveTimeout);
        System.out.println("\nHTTP 1.1: Connection keep alive is active for " + (keepAliveTimeout / 1000) + " secs");
    }

    private static void writeHttpResponse(OutputStream outputStream, String filePath) throws IOException {
        File file = new File(filePath);
        String contentType = getContentType(filePath);
        String charset = contentType.startsWith("text/") ? "; charset=UTF-8" : "";
        String responseHeader = buildResponseHeader1_1(contentType, charset, file);

        outputStream.write(responseHeader.getBytes());
        transferFileContent(outputStream, file);
    }

    private static String buildResponseHeader1_1(String contentType, String charset, File file) {
        return "HTTP/1.1 200 OK\r\n" +
                "Content-Type: " + contentType + charset + "\r\n" +
                "Content-Length: " + file.length() + "\r\n" +
                "Date: " + getCurrentDate() + "\r\n" +
                "Connection: keep-alive\r\n" +
                "\r\n";
    }

    private static void transferFileContent(OutputStream outputStream, File file) throws IOException {
        try (BufferedInputStream fileInputStream = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }
    }

    private static void flushAndHandleTimeout(OutputStream outputStream, Socket clientSocket) {
        try {
            outputStream.flush();
        } catch (SocketTimeoutException e) {
            System.out.println("Socket Timeout after KeepAlive");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private static String getContentType(String fileName) {
        // Extract file extension from fileName
        int lastDotIndex = fileName.lastIndexOf(".");
        if (lastDotIndex == -1) {
            return "Unsupported file type";
        }

        String extension = fileName.substring(lastDotIndex);

        switch (extension.toLowerCase()) {
            case ".html":
                return "text/html";
            case ".txt":
                return "text/plain";
            case ".jpg":
                return "image/jpg";
            case ".jpeg":
                return "image/jpeg";
            case ".png":
                return "image/png";
            case ".gif":
                return "image/gif";
            default:
                return "Unsupported file type";
        }
    }


    private static void sendErrorResponse(OutputStream outputStream, String responseCode, String requestHttpVersion) throws IOException {
        String errorMessage = "<html><head><title>Error</title></head><body><h1>" + responseCode + "</h1></body></html>";
        String response = "HTTP/" +requestHttpVersion + " " + responseCode + "\r\n" +
                          "Content-Type: text/html\r\n" +
                          "Content-Length: " + errorMessage.length() + "\r\n" +
                          "Date: " + getCurrentDate() + "\r\n" +
                          "\r\n" + errorMessage;

        outputStream.write(response.getBytes());

        outputStream.flush();
    }

    private static String getCurrentDate() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z");
        return dateFormat.format(new Date());
    }
}
