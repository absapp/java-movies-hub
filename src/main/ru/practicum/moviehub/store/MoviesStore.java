package ru.practicum.moviehub.store;

import ru.practicum.moviehub.model.Movie;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MoviesStore {

    private final List<Movie> movies = new ArrayList<>();

    public List<Movie> getMovies() {
        return List.copyOf(movies);
    }

    public void addMovie(Movie movie) {
        movies.add(movie);
    }

    public Optional<Movie> getMovie(int movieId) {
        return movies.stream().filter(m -> m.getId() == movieId).findFirst();
    }

    public void deleteMovie(int movieId) {
        movies.removeIf(movie -> movie.getId() == movieId);
    }

    public void clearListOfMovie() {
        movies.clear();
    }

}