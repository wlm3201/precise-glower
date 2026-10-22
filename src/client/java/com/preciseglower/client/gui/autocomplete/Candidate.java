package com.preciseglower.client.gui.autocomplete;

public record Candidate(String id, String label) {
    public static Candidate of(String id) {
        return new Candidate(id, "");
    }

    public static Candidate of(String id, String label) {
        return new Candidate(id, label == null ? "" : label);
    }
}
