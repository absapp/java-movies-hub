package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpServer;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.net.InetSocketAddress;

public class MoviesServer {

    private HttpServer server;
    private static final int PORT = 8080;
    private final MoviesStore store;

    public MoviesServer() {
        this.store = new MoviesStore();
        try {
            server = HttpServer.create(new InetSocketAddress(PORT), 0);
            server.createContext("/movies", new MoviesHandler(store));
        } catch (IOException e) {
            throw new RuntimeException("Не удалось создать HTTP-сервер на порту " + PORT, e);
        }
    }

    public void start() {
        if (server == null) {
            throw new IllegalStateException("Сервер не был создан");
        }
        server.start();
        System.out.println("Сервер запущен на порту " + PORT);
    }

    public void stop() {
        if (server == null) {
            return;
        }
        server.stop(0);
        System.out.println("Сервер остановлен");
    }

    public MoviesStore getStore() {
        return store;
    }
}