package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public abstract class BaseHttpHandler implements HttpHandler {

    protected static final String CT_JSON =  "application/json; charset=UTF-8";
    protected static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    protected void sendJson(HttpExchange ex, int status, String json) throws IOException {
        byte[] responseBytes = json.getBytes(DEFAULT_CHARSET);
        ex.getResponseHeaders().set("Content-Type", CT_JSON);
        try (OutputStream os = ex.getResponseBody()) {
            ex.sendResponseHeaders(status, responseBytes.length);
            os.write(responseBytes);
        }
    }

    protected void sendNoContent(HttpExchange ex) throws java.io.IOException {
        ex.getResponseHeaders().set("Content-Type", CT_JSON);
        ex.sendResponseHeaders(204, -1);
    }
}