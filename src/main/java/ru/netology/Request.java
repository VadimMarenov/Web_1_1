package ru.netology;

import java.util.List;

public class Request {
    private final String method;
    private final String path;
    private final String protocol;
    private final List<String> hadlers;
    private final String body;

    public Request(String method, String path, String protocol, List<String> hadlers, String body) {
        this.method = method;
        this.path = path;
        this.protocol = protocol;
        this.hadlers = hadlers;
        this.body = body;
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public String getProtocol() {
        return protocol;
    }

    public List<String> getHadlers() {
        return hadlers;
    }

    public String getBody() {
        return body;
    }
}
