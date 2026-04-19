package ru.practicum.moviehub.http;

import com.google.gson.Gson;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.practicum.moviehub.model.Movie;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MoviesApiTest {

    private static final String BASE = "http://localhost:8080";
    private static MoviesServer server;
    private static HttpClient client;
    private static Gson gson;

    @BeforeAll
    static void beforeAll() {
        server = new MoviesServer();
        server.start();
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        gson = new Gson();
    }

    @BeforeEach
    void beforeEach() {
        server.getStore().clearListOfMovie();
    }

    @AfterAll
    static void afterAll() {
        server.stop();
    }

    @Test
    void getMovies_whenEmpty_returnsEmptyArray() throws Exception {

        // создайте объект GET-запроса на эндпоинт /movies
        HttpRequest req = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(BASE + "/movies"))
                .build();

        // Обработчик тела запроса
        HttpResponse.BodyHandler<String> responseBodyHandler =
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);
        // Отправьте запрос
        HttpResponse<String> resp = client.send(req, responseBodyHandler);

        // Допишите проверку кода ответа
        assertEquals(200, resp.statusCode(), "GET /movies должен вернуть 200");

        // Допишите проверку заголовка Content-Type
        String contentTypeHeaderValue =
                resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        // проверка, что был возвращён массив
        String body = resp.body().trim();
        assertTrue(body.startsWith("[") && body.endsWith("]"),
                "Ожидается JSON-массив");
    }

    @Test
    void getMovies_whenNotEmpty_returnsMoviesArray() throws Exception {
        Movie testMovie = new Movie("Test Movie", 2023);
        server.getStore().addMovie(testMovie);

        HttpRequest req = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(BASE + "/movies"))
                .build();


        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200 , resp.statusCode());

        List<Movie> moviesFromResponse = gson.fromJson(resp.body(), new ListOfMoviesTypeToken().getType());

        assertNotNull(moviesFromResponse);
        assertFalse(moviesFromResponse.isEmpty());
        assertTrue(moviesFromResponse.stream().anyMatch(m -> "Test Movie".equals(m.getTitle())));

    }

    @Test
    void postMovies_whenValidData_returnsCreatedMovie() throws Exception {
        String newMovieJson = """
            {
                "title": "Inception",
                "year": 2010
            }
            """;

        HttpRequest req = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(newMovieJson, StandardCharsets.UTF_8))
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json; charset=UTF-8")
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(201, resp.statusCode(), "POST /movies должен вернуть 201 Created");

        String contentType = resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentType);

        Movie createdMovie = gson.fromJson(resp.body(), Movie.class);

        assertNotNull(createdMovie, "Ответ должен содержать созданный фильм");
        assertEquals("Inception", createdMovie.getTitle(), "Название должно совпадать");
        assertEquals(2010, createdMovie.getYear(), "Год должен совпадать");
        assertTrue(createdMovie.getId() > 0, "Фильму должен быть присвоен ID > 0");

        HttpRequest getReq = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(BASE + "/movies"))
                .build();

        HttpResponse<String> getResp = client.send(getReq, HttpResponse.BodyHandlers.ofString());
        List<Movie> allMovies = gson.fromJson(getResp.body(), new ListOfMoviesTypeToken().getType());

        assertTrue(allMovies.stream().anyMatch(m -> m.getId() == createdMovie.getId()),
                "Созданный фильм должен присутствовать в списке всех фильмов");
    }

    @Test
    void postMovies_whenWrongContentType_returns415() throws Exception {
        String newMovieJson = """
        {
            "title": "Inception",
            "year": 2010
        }
        """;

        HttpRequest req = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(newMovieJson, StandardCharsets.UTF_8))
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "text/plain")
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(415, resp.statusCode(), "Должен вернуться 415 Unsupported Media Type");

        String body = resp.body();
        assertTrue(body.contains("error"), "Ответ должен содержать поле 'error'");
        assertTrue(body.contains("Неподдерживаемый тип содержимого") || body.contains("Content-Type"),
                "Ответ должен указывать на проблему с Content-Type");
    }

    @Test
    void postMovies_whenEmptyTitle_returns422WithDetails() throws Exception {
        String newMovieJson = """
            {
                "title": "",
                "year": 2010
            }
            """;

        HttpRequest req = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(newMovieJson, StandardCharsets.UTF_8))
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json; charset=UTF-8")
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(422, resp.statusCode(), "Должен вернуться 422 Unprocessable Entity");

        String body = resp.body();
        assertTrue(body.contains("\"error\""), "Ответ должен содержать поле 'error'");
        assertTrue(body.contains("\"details\""), "Ответ должен содержать поле 'details'");

        assertTrue(body.contains("название") || body.contains("title"),
                "Детали должны указывать на проблему с названием");

        HttpRequest getReq = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(BASE + "/movies"))
                .build();

        HttpResponse<String> getResp = client.send(getReq, HttpResponse.BodyHandlers.ofString());
        List<Movie> allMovies = gson.fromJson(getResp.body(), new ListOfMoviesTypeToken().getType());

        assertTrue(allMovies.isEmpty(), "Фильм с пустым названием не должен быть добавлен");
    }

    @Test
    void postMovies_whenTitleTooLong_returns422WithDetails() throws Exception {
        String longTitle = "A".repeat(101);
        String newMovieJson = String.format("""
            {
                "title": "%s",
                "year": 2010
            }
            """, longTitle);

        HttpRequest req = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(newMovieJson, StandardCharsets.UTF_8))
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json; charset=UTF-8")
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(422, resp.statusCode());

        String body = resp.body();
        assertTrue(body.contains("100") || body.contains("длин"),
                "Детали должны указывать на превышение длины title");
    }

    @Test
    void postMovies_whenYearTooOld_returns422WithDetails() throws Exception {
        String newMovieJson = """
            {
                "title": "Old Movie",
                "year": 1887
            }
            """;

        HttpRequest req = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(newMovieJson, StandardCharsets.UTF_8))
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json; charset=UTF-8")
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(422, resp.statusCode());

        String body = resp.body();
        assertTrue(body.contains("1888") || body.contains("год"),
                "Детали должны указывать на некорректный год (слишком старый)");
    }

    @Test
    void postMovies_whenYearTooFuture_returns422WithDetails() throws Exception {
        int currentYear = java.time.Year.now().getValue();
        int tooFutureYear = currentYear + 2;

        String newMovieJson = String.format("""
            {
                "title": "Future Movie",
                "year": %d
            }
            """, tooFutureYear);

        HttpRequest req = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(newMovieJson, StandardCharsets.UTF_8))
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json; charset=UTF-8")
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(422, resp.statusCode());

        String body = resp.body();
        assertTrue(body.contains(String.valueOf(currentYear + 1)) || body.contains("год"),
                "Детали должны указывать на некорректный год (слишком будущий)");
    }

    @Test
    void postMovies_whenInvalidJson_returns422() throws Exception {
        String invalidJson = """
            {
                "title": "Incomplete",
                "year": 2010
            """;  // Нет закрывающей скобки

        HttpRequest req = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(invalidJson, StandardCharsets.UTF_8))
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json; charset=UTF-8")
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(422, resp.statusCode());

        String body = resp.body();
        assertTrue(body.contains("Некорректный JSON") || body.contains("JSON"),
                "Ответ должен указывать на проблему с форматом JSON");
    }

    @Test
    void getMovieById_whenExists_returnsMovie() throws Exception {
        String newMovieJson = """
            {
                "title": "The Matrix",
                "year": 1999
            }
            """;

        HttpRequest postReq = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(newMovieJson, StandardCharsets.UTF_8))
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json; charset=UTF-8")
                .build();

        HttpResponse<String> postResp = client.send(postReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(201, postResp.statusCode(), "Фильм должен быть создан");

        Movie createdMovie = gson.fromJson(postResp.body(), Movie.class);
        int movieId = createdMovie.getId();

        HttpRequest getReq = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(BASE + "/movies/" + movieId))
                .build();

        HttpResponse<String> getResp = client.send(getReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, getResp.statusCode(), "GET /movies/{id} должен вернуть 200");

        String contentType = getResp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentType);

        Movie retrievedMovie = gson.fromJson(getResp.body(), Movie.class);
        assertNotNull(retrievedMovie, "Ответ должен содержать фильм");
        assertEquals(movieId, retrievedMovie.getId(), "ID должен совпадать");
        assertEquals("The Matrix", retrievedMovie.getTitle(), "Название должно совпадать");
        assertEquals(1999, retrievedMovie.getYear(), "Год должен совпадать");
    }

    @Test
    void getMovieById_whenNotExists_returns404() throws Exception {
        int nonExistentId = 99999;

        HttpRequest getReq = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(BASE + "/movies/" + nonExistentId))
                .build();

        HttpResponse<String> getResp = client.send(getReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(404, getResp.statusCode(), "Должен вернуться 404 Not Found");

        String body = getResp.body();
        assertTrue(body.contains("\"error\""), "Ответ должен содержать поле 'error'");
        assertTrue(body.contains("Фильм не найден") || body.contains("не найден"),
                "Ответ должен указывать, что фильм не найден");
    }

    @Test
    void getMovieById_whenIdNotNumber_returns400() throws Exception {
        String invalidId = "abc";

        HttpRequest getReq = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(BASE + "/movies/" + invalidId))
                .build();

        HttpResponse<String> getResp = client.send(getReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, getResp.statusCode(), "Должен вернуться 400 Bad Request");

        String body = getResp.body();
        assertTrue(body.contains("\"error\""), "Ответ должен содержать поле 'error'");
        assertTrue(body.contains("Некорректный ID") || body.contains("ID"),
                "Ответ должен указывать на некорректный ID");
    }

    @Test
    void getMovieById_whenNegativeId_returns404() throws Exception {
        HttpRequest getReq = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(BASE + "/movies/-1"))
                .build();

        HttpResponse<String> getResp = client.send(getReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(404, getResp.statusCode(), "Отрицательный ID должен вернуть 404");
    }

    @Test
    void deleteMovieById_whenExists_returns204AndRemovesMovie() throws Exception {
        String newMovieJson = """
            {
                "title": "Movie to Delete",
                "year": 2020
            }
            """;

        HttpRequest postReq = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(newMovieJson, StandardCharsets.UTF_8))
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json; charset=UTF-8")
                .build();

        HttpResponse<String> postResp = client.send(postReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(201, postResp.statusCode(), "Фильм должен быть создан");

        Movie createdMovie = gson.fromJson(postResp.body(), Movie.class);
        int movieId = createdMovie.getId();

        HttpRequest getBeforeReq = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(BASE + "/movies/" + movieId))
                .build();

        HttpResponse<String> getBeforeResp = client.send(getBeforeReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, getBeforeResp.statusCode(), "Фильм должен существовать до удаления");

        HttpRequest deleteReq = HttpRequest.newBuilder()
                .DELETE()
                .uri(URI.create(BASE + "/movies/" + movieId))
                .build();

        HttpResponse<String> deleteResp = client.send(deleteReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(204, deleteResp.statusCode(), "DELETE /movies/{id} должен вернуть 204 No Content");

        String body = deleteResp.body();
        assertTrue(body == null || body.isEmpty(), "Ответ 204 не должен содержать тело");

        HttpRequest getAfterReq = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(BASE + "/movies/" + movieId))
                .build();

        HttpResponse<String> getAfterResp = client.send(getAfterReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, getAfterResp.statusCode(), "После удаления фильм не должен существовать");

        HttpRequest getAllReq = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(BASE + "/movies"))
                .build();

        HttpResponse<String> getAllResp = client.send(getAllReq, HttpResponse.BodyHandlers.ofString());
        List<Movie> allMovies = gson.fromJson(getAllResp.body(), new ListOfMoviesTypeToken().getType());

        assertTrue(allMovies.stream().noneMatch(m -> m.getId() == movieId),
                "Удаленный фильм не должен присутствовать в списке всех фильмов");
    }

    @Test
    void deleteMovieById_whenNotExists_returns404() throws Exception {
        int nonExistentId = 99999;

        HttpRequest deleteReq = HttpRequest.newBuilder()
                .DELETE()
                .uri(URI.create(BASE + "/movies/" + nonExistentId))
                .build();

        HttpResponse<String> deleteResp = client.send(deleteReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(404, deleteResp.statusCode(), "Должен вернуться 404 Not Found");

        assertErrorResponse(deleteResp.body(), "Фильм не найден");

        HttpRequest getAllReq = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(BASE + "/movies"))
                .build();

        HttpResponse<String> getAllResp = client.send(getAllReq, HttpResponse.BodyHandlers.ofString());
        List<Movie> allMovies = gson.fromJson(getAllResp.body(), new ListOfMoviesTypeToken().getType());

        assertTrue(allMovies.isEmpty(), "Список фильмов не должен измениться");
    }

    @Test
    void deleteMovieById_whenIdNotNumber_returns400() throws Exception {
        String invalidId = "abc";

        HttpRequest deleteReq = HttpRequest.newBuilder()
                .DELETE()
                .uri(URI.create(BASE + "/movies/" + invalidId))
                .build();

        HttpResponse<String> deleteResp = client.send(deleteReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, deleteResp.statusCode(), "Должен вернуться 400 Bad Request");

        assertErrorResponse(deleteResp.body(), "Некорректный ID");
    }

    @Test
    void deleteMovieById_whenAlreadyDeleted_returns404() throws Exception {
        String newMovieJson = """
            {
                "title": "Double Delete Movie",
                "year": 2021
            }
            """;

        HttpRequest postReq = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(newMovieJson, StandardCharsets.UTF_8))
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json; charset=UTF-8")
                .build();

        HttpResponse<String> postResp = client.send(postReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, postResp.statusCode());

        Movie createdMovie = gson.fromJson(postResp.body(), Movie.class);
        int movieId = createdMovie.getId();

        HttpRequest firstDeleteReq = HttpRequest.newBuilder()
                .DELETE()
                .uri(URI.create(BASE + "/movies/" + movieId))
                .build();

        HttpResponse<String> firstDeleteResp = client.send(firstDeleteReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(204, firstDeleteResp.statusCode(), "Первое удаление должно быть успешным");

        HttpRequest secondDeleteReq = HttpRequest.newBuilder()
                .DELETE()
                .uri(URI.create(BASE + "/movies/" + movieId))
                .build();

        HttpResponse<String> secondDeleteResp = client.send(secondDeleteReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, secondDeleteResp.statusCode(), "Повторное удаление должно вернуть 404");

        assertErrorResponse(secondDeleteResp.body(), "Фильм не найден");
    }

    @Test
    void deleteMovieById_whenNegativeId_returns404() throws Exception {
        HttpRequest deleteReq = HttpRequest.newBuilder()
                .DELETE()
                .uri(URI.create(BASE + "/movies/-1"))
                .build();

        HttpResponse<String> deleteResp = client.send(deleteReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(404, deleteResp.statusCode(), "Отрицательный ID должен вернуть 404");
        assertErrorResponse(deleteResp.body(), "Фильм не найден");
    }

    @Test
    void getMoviesByYear_whenMoviesExist_returnsFilteredList() throws Exception {
        Movie movie1 = new Movie("Movie 2020", 2020);
        Movie movie2 = new Movie("Another 2020", 2020);
        Movie movie3 = new Movie("Movie 2021", 2021);

        server.getStore().addMovie(movie1);
        server.getStore().addMovie(movie2);
        server.getStore().addMovie(movie3);

        HttpRequest req = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(BASE + "/movies?year=2020"))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode(), "GET /movies?year=2020 должен вернуть 200");

        String contentType = resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentType);

        List<Movie> filteredMovies = gson.fromJson(resp.body(), new ListOfMoviesTypeToken().getType());

        assertNotNull(filteredMovies, "Ответ должен содержать список фильмов");
        assertEquals(2, filteredMovies.size(), "Должно вернуться 2 фильма 2020 года");

        assertTrue(filteredMovies.stream().allMatch(m -> m.getYear() == 2020),
                "Все фильмы в ответе должны быть 2020 года");

        assertTrue(filteredMovies.stream().anyMatch(m -> "Movie 2020".equals(m.getTitle())),
                "Первый фильм должен быть в ответе");
        assertTrue(filteredMovies.stream().anyMatch(m -> "Another 2020".equals(m.getTitle())),
                "Второй фильм должен быть в ответе");
    }

    @Test
    void getMoviesByYear_whenNoMoviesForYear_returnsEmptyArray() throws Exception {
        Movie movie1 = new Movie("Movie 2020", 2020);
        Movie movie2 = new Movie("Another 2020", 2020);

        server.getStore().addMovie(movie1);
        server.getStore().addMovie(movie2);

        HttpRequest req = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(BASE + "/movies?year=1999"))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode(), "Должен вернуться 200 даже если фильмов нет");

        List<Movie> filteredMovies = gson.fromJson(resp.body(), new ListOfMoviesTypeToken().getType());

        assertNotNull(filteredMovies, "Ответ должен содержать список (даже пустой)");
        assertTrue(filteredMovies.isEmpty(), "Список должен быть пустым");

        String body = resp.body().trim();
        assertTrue(body.startsWith("[") && body.endsWith("]"), "Ожидается JSON-массив");
    }

    @Test
    void getMoviesByYear_whenYearNotNumber_returns400() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(BASE + "/movies?year=abc"))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, resp.statusCode(), "Должен вернуться 400 Bad Request");

        assertErrorResponse(resp.body(), "Некорректный параметр запроса — 'year'");
    }

    @Test
    void getMoviesByYear_whenYearParameterMissing_returnsAllMovies() throws Exception {
        Movie movie1 = new Movie("Movie 2020", 2020);
        Movie movie2 = new Movie("Movie 2021", 2021);

        server.getStore().addMovie(movie1);
        server.getStore().addMovie(movie2);

        HttpRequest req = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(BASE + "/movies"))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode());

        List<Movie> allMovies = gson.fromJson(resp.body(), new ListOfMoviesTypeToken().getType());
        assertEquals(2, allMovies.size(), "Должны вернуться все фильмы");
    }

    @Test
    void getMoviesByYear_whenYearEmpty_returns400() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(BASE + "/movies?year="))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, resp.statusCode(), "Пустой параметр year должен вернуть 400");

        assertErrorResponse(resp.body(), "Некорректный параметр запроса");
    }

    @Test
    void getMoviesByYear_whenMultipleParameters_ignoresExtraParams() throws Exception {
        Movie movie1 = new Movie("Movie 2020", 2020);
        server.getStore().addMovie(movie1);

        HttpRequest req = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(BASE + "/movies?year=2020&sort=desc&limit=10"))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode());

        List<Movie> filteredMovies = gson.fromJson(resp.body(), new ListOfMoviesTypeToken().getType());
        assertEquals(1, filteredMovies.size(), "Должен вернуть фильмы 2020 года");
    }

    private void assertErrorResponse(String body, String expectedErrorSubstring) {
        assertTrue(body.contains("\"error\""), "Ответ должен содержать поле 'error'");
        assertTrue(body.contains("\"details\""), "Ответ должен содержать поле 'details'");
        assertTrue(body.contains(expectedErrorSubstring),
                "Ответ должен содержать текст ошибки: " + expectedErrorSubstring);
    }
}