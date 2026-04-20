package ru.practicum.moviehub.http;

import com.google.gson.Gson;

import com.google.gson.JsonParseException;
import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.enums.Endpoint;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.util.*;

public class MoviesHandler extends BaseHttpHandler {

    private final MoviesStore store;
    private final Gson gson;
    private static final int MAX_TITLE_LENGTH = 100;
    private static final int MIN_YEAR = 1888;

    public MoviesHandler(MoviesStore store) {
        this.store = store;
        this.gson = new Gson();
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        Endpoint endpoint = getEndpoint(ex.getRequestURI().getPath(),
                ex.getRequestMethod(), ex.getRequestURI().getQuery());
        switch (endpoint) {
            case GET_MOVIES: {
                handleGetMovies(ex);
                break;
            }
            case POST_MOVIES: {
                handlePostMovies(ex);
                break;
            }
            case GET_MOVIE_BY_ID: {
                handleGetMovieById(ex);
                break;
            }
            case DELETE_MOVIES_BY_ID: {
                handleDeleteMovieById(ex);
                break;
            }
            case GET_MOVIE_BY_YEAR: {
                handleGetMoviesByYear(ex);
                break;
            }
            default: {
                handleGetUnknownEndpoint(ex);
                break;
            }
        }
    }

    private Endpoint getEndpoint(String requestPath, String requestMethod, String query) {
        String[] path = requestPath.split("/");
        if (path.length == 2 && path[1].equals("movies")) {
            if ("GET".equalsIgnoreCase(requestMethod)) {
                if (query != null && query.startsWith("year=")) {
                    return Endpoint.GET_MOVIE_BY_YEAR;
                }
                return Endpoint.GET_MOVIES;
            } else if ("POST".equalsIgnoreCase(requestMethod)) {
                return Endpoint.POST_MOVIES;
            }
        }

        if (path.length == 3 && path[1].equals("movies")) {
            if ("GET".equalsIgnoreCase(requestMethod)) {
                return Endpoint.GET_MOVIE_BY_ID;
            } else if ("DELETE".equalsIgnoreCase(requestMethod)) {
                return Endpoint.DELETE_MOVIES_BY_ID;
            }
        }
        return Endpoint.UNKNOWN;
    }

    private void handleGetMovies(HttpExchange httpExchange) throws IOException {
        List<Movie> allMovies = store.getMovies();
        String jsonResponse = gson.toJson(allMovies);
        sendJson(httpExchange, 200, jsonResponse);
    }

    private void handlePostMovies(HttpExchange ex) throws IOException {
        String contentType = ex.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.startsWith("application/json")) {
            sendError(ex, 415, "Неподдерживаемый тип содержимого",
                    List.of("Заголовок Content-Type должен быть application/json"));
            return;
        }

        String body = new String(ex.getRequestBody().readAllBytes(), DEFAULT_CHARSET);

        Movie movie;
        try {
            movie = gson.fromJson(body, Movie.class);
        } catch (JsonParseException e) {
            sendError(ex, 422, "Ошибка валидации", List.of("Некорректный JSON"));
            return;
        }

        if (movie == null) {
            sendError(ex, 422, "Ошибка валидации", List.of("Тело запроса не может быть пустым"));
            return;
        }

        List<String> validationErrors = validateMovie(movie);
        if (!validationErrors.isEmpty()) {
            sendError(ex, 422, "Ошибка валидации", validationErrors);
            return;
        }

        store.addMovie(movie);
        String jsonResponse = gson.toJson(movie);
        sendJson(ex, 201, jsonResponse);
    }

    private void handleGetMovieById(HttpExchange ex) throws IOException {
        Optional<Integer> optionalId = getMovieId(ex);
        if (optionalId.isEmpty()) {
            sendError(ex, 400, "Некорректный ID", List.of("ID должен быть целым числом"));
            return;
        }

        int movieId = optionalId.get();
        Optional<Movie> optionalMovie = store.getMovie(movieId);
        if (optionalMovie.isEmpty()) {
            sendError(ex, 404, "Фильм не найден", List.of("Фильм с ID " + movieId + " не существует"));
            return;
        }

        Movie movie = optionalMovie.get();
        String jsonResponse = gson.toJson(movie);
        sendJson(ex, 200, jsonResponse);
    }

    private void handleDeleteMovieById(HttpExchange ex) throws IOException {
        Optional<Integer> optionalId = getMovieId(ex);
        if (optionalId.isEmpty()) {
            sendError(ex, 400, "Некорректный ID", List.of("ID должен быть целым числом"));
            return;
        }

        int id = optionalId.get();
        Optional<Movie> optionalMovie = store.getMovie(id);
        if (optionalMovie.isEmpty()) {
            sendError(ex, 404, "Фильм не найден", List.of("Фильм с ID " + id + " не существует"));
            return;
        }

        store.deleteMovie(id);
        sendNoContent(ex);
    }

    private void handleGetMoviesByYear(HttpExchange ex) throws IOException {
        String query = ex.getRequestURI().getQuery();
        if (query == null || !query.startsWith("year=")) {
            sendError(ex, 400, "Некорректный параметр запроса — 'year'",
                    List.of("Ожидается параметр 'year'"));
            return;
        }

        String yearParam = query.substring(5);

        int ampersandIndex = yearParam.indexOf('&');
        if (ampersandIndex != -1) {
            yearParam = yearParam.substring(0, ampersandIndex);
        }

        if (yearParam.isEmpty()) {
            sendError(ex, 400, "Некорректный параметр запроса — 'year'",
                    List.of("Параметр 'year' не может быть пустым"));
            return;
        }

        int year;
        try {
            year = Integer.parseInt(yearParam);
        } catch (NumberFormatException e) {
            sendError(ex, 400, "Некорректный параметр запроса — 'year'",
                    List.of("Параметр 'year' должен быть целым числом"));
            return;
        }

        List<Movie> filteredMovies = store.getMovies().stream()
                .filter(movie -> movie.getYear() == year)
                .toList();

        String jsonResponse = gson.toJson(filteredMovies);
        sendJson(ex, 200, jsonResponse);
    }

    private List<String> validateMovie(Movie movie) {
        List<String> errors = new ArrayList<>();

        if (movie.getTitle() == null || movie.getTitle().trim().isEmpty()) {
            errors.add("название не должно быть пустым");
        } else if (movie.getTitle().length() > MAX_TITLE_LENGTH) {
            errors.add("название не должно превышать " + MAX_TITLE_LENGTH + " символов");
        }

        int currentYear = java.time.Year.now().getValue();
        if (movie.getYear() < MIN_YEAR) {
            errors.add("год должен быть не ранее " + MIN_YEAR);
        } else if (movie.getYear() > currentYear + 1) {
            errors.add("год должен быть не позже " + (currentYear + 1));
        }

        return errors;
    }

    private Optional<Integer> getMovieId(HttpExchange exchange) {
        String[] pathParts = exchange.getRequestURI().getPath().split("/");
        if (pathParts.length < 3) {
            return Optional.empty();
        }

        try {
            return Optional.of(Integer.parseInt(pathParts[2]));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private void sendError(HttpExchange ex, int statusCode, String error, List<String> details) throws IOException {
        ErrorResponse errorResponse = new ErrorResponse(error, details);
        sendJson(ex, statusCode, gson.toJson(errorResponse));
    }

    private void handleGetUnknownEndpoint(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.matches("/movies(/\\d+)?")) {
            sendError(ex, 405, "Метод не поддерживается",
                    List.of("Метод " + ex.getRequestMethod() + " не поддерживается для этого ресурса"));
        } else {
            sendError(ex, 404, "Эндпоинт не найден", List.of("Запрошенный ресурс не существует"));
        }
    }
}
