package com.task09;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.lambda.LambdaUrlConfig;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.model.TracingMode;
import com.syndicate.deployment.model.lambda.url.AuthType;
import com.syndicate.deployment.model.lambda.url.InvokeMode;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import com.amazonaws.xray.interceptors.TracingInterceptor;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@LambdaHandler(
        lambdaName = "processor",
        roleName = "processor-role",
        isPublishVersion = true,
        aliasName = "${lambdas_alias_name}",
        logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED,
        tracingMode = TracingMode.Active
)
@LambdaUrlConfig(authType = AuthType.NONE, invokeMode = InvokeMode.BUFFERED)
@EnvironmentVariable(key = "target_table", value = "${target_table}")
public class Processor implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {
    private static final String OPEN_METEO_API_URL = "https://api.open-meteo.com/v1/forecast?latitude=52.52&longitude=13.41&current=temperature_2m,wind_speed_10m&hourly=temperature_2m,relative_humidity_2m,wind_speed_10m";
    private static final String TABLE_NAME = System.getenv("target_table");
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final DynamoDbClient dynamoDB = DynamoDbClient.builder()
            .region(Region.EU_CENTRAL_1)
            .overrideConfiguration(ClientOverrideConfiguration.builder()
                    .addExecutionInterceptor(new TracingInterceptor())
                    .build())
            .build();

    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        APIGatewayV2HTTPEvent.RequestContext.Http http = event.getRequestContext().getHttp();
        if ("GET".equals(http.getMethod()) && http.getPath().contains("/")) {
            try {
                storeDataToDB(context.getLogger());
                return APIGatewayV2HTTPResponse.builder()
                        .withStatusCode(200)
                        .build();
            } catch (Exception e) {
                context.getLogger().log("An error occurred while getting weather data: " + e.getMessage());
                context.getLogger().log(Arrays.toString(e.getStackTrace()));
                return APIGatewayV2HTTPResponse.builder()
                        .withBody("An error occurred with the request")
                        .withStatusCode(500)
                        .build();
            }
        }
        return APIGatewayV2HTTPResponse.builder()
                .withBody("Path not found")
                .withStatusCode(404)
                .build();
    }

    public void storeDataToDB(LambdaLogger logger) throws JsonProcessingException {
        String weatherJSON = getWeatherData();
        Map<String, Object> jsonMap = objectMapper.readValue(weatherJSON, Map.class);
        Map<String, AttributeValue> forecastValues = mapJSONToAttributeValues(jsonMap);

        Map<String, AttributeValue> dataToStore = new HashMap<>();
        dataToStore.put("id", AttributeValue.builder().s(UUID.randomUUID().toString()).build());
        dataToStore.put("forecast", AttributeValue.builder().m(forecastValues).build());

        PutItemRequest putItemRequest = PutItemRequest.builder()
                .tableName(TABLE_NAME)
                .item(dataToStore)
                .build();
        try {
            dynamoDB.putItem(putItemRequest);
        } catch (DynamoDbException ex) {
            logger.log("An error occurred while storing in DB: " + ex.getMessage());
            throw new RuntimeException(ex);
        }
    }

    private String getWeatherData() {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(OPEN_METEO_API_URL);
            return EntityUtils.toString(httpClient.execute(request).getEntity());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, AttributeValue> mapJSONToAttributeValues(Map<String, Object> jsonMap) {
        Map<String, AttributeValue> attributeValuesMap = new HashMap<>();
        Map<String, AttributeValue> hourlyUnitsAttributeValueMap = new HashMap<>();
        Map<String, Object> hourlyUnitsMap = (Map<String, Object>) jsonMap.get("hourly_units");

        hourlyUnitsAttributeValueMap.put("temperature_2m", AttributeValue.builder().s((String) hourlyUnitsMap.get("temperature_2m")).build());
        hourlyUnitsAttributeValueMap.put("time", AttributeValue.builder().s((String) hourlyUnitsMap.get("time")).build());

        attributeValuesMap.put("elevation", AttributeValue.builder().n(String.valueOf(jsonMap.get("elevation"))).build());
        attributeValuesMap.put("generationtime_ms", AttributeValue.builder().n(String.valueOf(jsonMap.get("generationtime_ms"))).build());
        attributeValuesMap.put("latitude", AttributeValue.builder().n(String.valueOf(jsonMap.get("latitude"))).build());
        attributeValuesMap.put("longitude", AttributeValue.builder().n(String.valueOf(jsonMap.get("longitude"))).build());
        attributeValuesMap.put("timezone", AttributeValue.builder().s((String) jsonMap.get("timezone")).build());
        attributeValuesMap.put("timezone_abbreviation", AttributeValue.builder().s((String) jsonMap.get("timezone_abbreviation")).build());
        attributeValuesMap.put("utc_offset_seconds", AttributeValue.builder().n(String.valueOf(jsonMap.get("utc_offset_seconds"))).build());
        attributeValuesMap.put("hourly", AttributeValue.builder().m(constructHourlyAttributeValues((Map<String, Object>) jsonMap.get("hourly"))).build());
        attributeValuesMap.put("hourly_units", AttributeValue.builder().m(hourlyUnitsAttributeValueMap).build());

        return attributeValuesMap;
    }

    private Map<String, AttributeValue> constructHourlyAttributeValues(Map<String, Object> hourlyMap) {
        Map<String, AttributeValue> attributeValuesMap = new HashMap<>();
        List<AttributeValue> temperatures = ((List<Object>) hourlyMap.get("temperature_2m")).stream().map(temp -> AttributeValue.builder().n(String.valueOf(temp)).build()).collect(Collectors.toList());
        List<AttributeValue> timeList = ((List<Object>) hourlyMap.get("time")).stream().map(temp -> AttributeValue.builder().s(String.valueOf(temp)).build()).collect(Collectors.toList());
        attributeValuesMap.put("temperature_2m", AttributeValue.builder().l(temperatures).build());
        attributeValuesMap.put("time", AttributeValue.builder().l(timeList).build());

        return attributeValuesMap;
    }
}
