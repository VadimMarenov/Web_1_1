package ru.netology;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private final String GET = "GET";
    private final String POST = "POST";
    private final int POOL_SIZE = 64;
    private final List<String> allowedMethods = List.of(GET, POST);
    private final Map<String, Map<String, Handler>> allHandlers;
    private final ExecutorService executorService;

    public Server() {
        this.allHandlers = new ConcurrentHashMap<>();
        this.executorService = Executors.newFixedThreadPool(POOL_SIZE);
    }

    public void init(int port) {
        try (final var serverSocket = new ServerSocket(port)) {
            while (true) {
                final var socket = serverSocket.accept();
                executorService.submit(() -> connect(socket));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void connect(Socket socket) {
        try (final var in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             final var out = new BufferedOutputStream(socket.getOutputStream())) {
            final Request request = parse(socket);

            if (!allHandlers.containsKey(request.getMethod())) {
                send404ToClient(out);
                return;
            }
            if (!allHandlers.get(request.getMethod()).containsKey(request.getPath())) {
                send404ToClient(out);
                return;
            }
            allHandlers.get(request.getMethod()).get(request.getPath()).handle(request, out);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public Request parse(Socket socket) throws IOException {
        final var in = new BufferedInputStream(socket.getInputStream());
        final var out = new BufferedOutputStream(socket.getOutputStream());

        final var limit = 4096;
        in.mark(limit);
        final var buffer = new byte[limit];
        final var read = in.read(buffer);

        // Ищем request line
        final var requestLineDelimiter = new byte[]{'\r', '\n'};
        final var requestLineEnd = indexOf(buffer, requestLineDelimiter, 0, read);
        if (requestLineEnd == -1) {
            badRequest(out);
            socket.close();
        }

        //Читаем request line
        final var requestLine = new String(Arrays.copyOf(buffer, requestLineEnd)).split(" ");
        if (requestLine.length != 3) {
            badRequest(out);
            socket.close();
        }
        final var method = requestLine[0];
        if (!allowedMethods.contains(method)) {
            badRequest(out);
            socket.close();
        }
        final var path = requestLine[1];
        if (!path.startsWith("/")) {
            badRequest(out);
            socket.close();
        }
        final var protocol = requestLine[2];

        //Ищем заголовки
        final var headersDelimiter = new byte[]{'\r', '\n', '\r', '\n'};
        final var headersStart = requestLineEnd + requestLineDelimiter.length;
        final var headersEnd = indexOf(buffer, headersDelimiter, headersStart, read);
        if (headersEnd == -1) {
            badRequest(out);
            socket.close();
        }
        in.reset();
        in.skip(headersStart);
        final var headersBytes = in.readNBytes(headersEnd - headersStart);
        final var headers = Arrays.asList(new String(headersBytes).split("\r\n"));
        System.out.println(headers);

        // для GET тела нет
        final int length;
        final byte[] bodyBytes;
        String body = "";
        if (!method.equals("GET")) {
            in.skip(headersDelimiter.length);
            // вычитываем Content-Length, чтобы прочитать body
            final var contentLength = extractHeader(headers, "Content-Length");
            if (contentLength.isPresent()) {
                length = Integer.parseInt(contentLength.get());
                bodyBytes = in.readNBytes(length);
                body = new String(bodyBytes);
                System.out.println(body);
            }
        }
        return new Request(method, path, protocol, headers, body);
    }

    private Optional<String> extractHeader(List<String> headers, String header) {
        return headers.stream()
                .filter(o -> o.startsWith(header))
                .map(o -> o.substring(o.indexOf(" ")))
                .map(String::trim)
                .findFirst();
    }

    private static int indexOf(byte[] array, byte[] target, int start, int max) {
        outer:
        for (int i = start; i < max - target.length + 1; i++) {
            for (int j = 0; j < target.length; j++) {
                if (array[i + j] != target[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static void badRequest(BufferedOutputStream out) throws IOException {
        out.write((
                "HTTP/1.1 400 Bad Request\r\n" +
                        "Content-Length: 0\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
        ).getBytes());
        out.flush();
    }

    private void send404ToClient(BufferedOutputStream out) throws IOException {
        out.write((
                "HTTP/1.1 404 Not Found\r\n" +
                        "Content-Length: 0\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
        ).getBytes());
        out.flush();
    }

    public void addHandler(String method, String path, Handler handler) {
        if (!allHandlers.containsKey(method)) {
            allHandlers.put(method, new ConcurrentHashMap<>());
        }
        allHandlers.get(method).put(path, handler);

    }
}
