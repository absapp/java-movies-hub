package ru.practicum.moviehub.model;

import java.util.HashSet;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

public class Movie {

    private String title;
    private int year;
    private int id;
    private static Set<Integer> usedIds = new HashSet<>();

    public Movie() {
        this.id = generateUniqueId();
    }

    public Movie(String title, int year) {
        this();
        this.title = title;
        this.year = year;
    }

    private int generateUniqueId() {
        int newId;
        do {
            newId = new Random().nextInt(10000);  // больший диапазон
        } while (usedIds.contains(newId));
        usedIds.add(newId);
        return newId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Movie movie = (Movie) o;
        return year == movie.year && Objects.equals(title, movie.title);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, year);
    }

    public int getId() {
        return id;
    }

    @Override
    public String toString() {
        return "Movie{" +
                "title='" + title + '\'' +
                ", year=" + year +
                ", id=" + id +
                '}';
    }

    public void setId(int id) {
        this.id = id;
    }
}