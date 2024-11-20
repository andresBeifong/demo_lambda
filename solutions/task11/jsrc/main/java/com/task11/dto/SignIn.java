package com.task11.dto;

import org.json.JSONObject;

public record SignIn(String email, String password) {

    public SignIn {
        if (email == null || password == null) {
            throw new IllegalArgumentException("Missing or incomplete data.");
        }
    }

    public static SignIn fromJson(String jsonString) {
        JSONObject json = new JSONObject(jsonString);
        String email = json.optString("email", null);
        String password = json.optString("password", null);

        return new SignIn(email, password);
    }

}