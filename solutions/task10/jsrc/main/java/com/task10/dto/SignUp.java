package com.task10.dto;

import org.json.JSONObject;

public record SignUp(String email, String password, String firstName, String lastName) {

    public SignUp {
        if (email == null || password == null || firstName == null || lastName == null) {
            throw new IllegalArgumentException("Missing or incomplete data.");
        }
    }

    public static SignUp fromJson(String jsonString) {
        JSONObject json = new JSONObject(jsonString);
        String email = json.optString("email", null);
        String password = json.optString("password", null);
        String firstName = json.optString("firstName", null);
        String lastName = json.optString("lastName", null);

        return new SignUp(email, password, firstName, lastName);
    }
}
