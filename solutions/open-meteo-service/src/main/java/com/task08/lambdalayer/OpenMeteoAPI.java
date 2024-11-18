package com.task08.lambdalayer;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;

/**
 * Hello world!
 *
 */
public class OpenMeteoAPI
{
    private static final String OPEN_METEO_API_URL = "https://api.open-meteo.com/v1/forecast?latitude=52.52&longitude=13.41&current=temperature_2m,wind_speed_10m&hourly=temperature_2m,relative_humidity_2m,wind_speed_10m";
    public String getData() throws IOException {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(OPEN_METEO_API_URL);
            return EntityUtils.toString(httpClient.execute(request).getEntity());
        }
    }
}
