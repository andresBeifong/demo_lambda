package com.task10.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.task10.ApiHandler;
import org.json.JSONObject;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class PostReservationsHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent requestEvent, Context context) {
        try {
            JSONObject tableRequest = new JSONObject(requestEvent.getBody());

            context.getLogger().log("Data input: " + tableRequest);

            if(!tableExists(String.valueOf(tableRequest.get("tableNumber")))){
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withBody(new JSONObject().put("error", "Table does not exist.").toString());
            }

            List<Map<String, AttributeValue>> existingReservations = existingReservations(tableRequest);
            if(checkForOverlap(existingReservations, tableRequest.getString("slotTimeStart"), tableRequest.getString("slotTimeEnd"))){
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withBody(new JSONObject().put("error", "Reservation time slot overlaps with an existing reservation.").toString());
            }

            Map<String, AttributeValue> tableDataMap = new HashMap<>();
            String reservationId = UUID.randomUUID().toString();

            tableDataMap.put("id", AttributeValue.builder().s(reservationId).build());
            tableDataMap.put("tableNumber", AttributeValue.builder().n(String.valueOf(tableRequest.get("tableNumber"))).build());
            tableDataMap.put("clientName", AttributeValue.builder().s(String.valueOf(tableRequest.get("clientName"))).build());
            tableDataMap.put("phoneNumber", AttributeValue.builder().s(String.valueOf(tableRequest.get("phoneNumber"))).build());
            tableDataMap.put("reservationDate", AttributeValue.builder().s(String.valueOf(tableRequest.get("date"))).build());
            tableDataMap.put("slotTimeStart", AttributeValue.builder().s(String.valueOf(tableRequest.get("slotTimeStart"))).build());
            tableDataMap.put("slotTimeEnd", AttributeValue.builder().s(String.valueOf(tableRequest.get("slotTimeEnd"))).build());

            PutItemRequest putItemRequest = PutItemRequest.builder().tableName(ApiHandler.RESERVATIONS_TABLE_NAME).item(tableDataMap).build();

            ApiHandler.dynamoDB.putItem(putItemRequest);

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withBody(new JSONObject().put("reservationId", reservationId).toString());
        } catch (DynamoDbException e) {
            context.getLogger().log("Error occurred: " + e.getMessage());
            context.getLogger().log(Arrays.toString(e.getStackTrace()));
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withBody(new JSONObject().put("error", e.getMessage()).toString());
        }
    }

    private List<Map<String, AttributeValue>> existingReservations(JSONObject tableJSON) {
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":num", AttributeValue.builder().n(String.valueOf(tableJSON.get("tableNumber"))).build());
        expressionAttributeValues.put(":d", AttributeValue.builder().s(String.valueOf(tableJSON.get("date"))).build());

        QueryRequest queryRequest = QueryRequest.builder()
                .tableName(ApiHandler.RESERVATIONS_TABLE_NAME)
                .indexName("table_key_index")
                .keyConditionExpression("tableNumber =:num")
                .filterExpression("reservationDate = :d")
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        QueryResponse result = ApiHandler.dynamoDB.query(queryRequest);
        return result.items();
    }

    private boolean tableExists(String tableNumber) {
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":num", AttributeValue.builder().n(tableNumber).build());

        QueryRequest queryRequest = QueryRequest.builder()
                .tableName(ApiHandler.TABLES_TABLE_NAME)
                .indexName("table_number_key_index")
                .keyConditionExpression("number =:num")
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        QueryResponse result = ApiHandler.dynamoDB.query(queryRequest);
        return !result.items().isEmpty();
    }

    private boolean checkForOverlap(List<Map<String, AttributeValue>> existingReservations, String startTime, String endTime) {


        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
        LocalTime start = LocalTime.parse(startTime, formatter);
        LocalTime end = LocalTime.parse(endTime, formatter);

        for (Map<String, AttributeValue> reservation : existingReservations) {
            LocalTime existingStart = LocalTime.parse(reservation.get("slotTimeStart").s(), formatter);
            LocalTime existingEnd = LocalTime.parse(reservation.get("slotTimeEnd").s(), formatter);

            if (start.isBefore(existingEnd) && end.isAfter(existingStart)) {
                return true;
            }
        }
        return false;
    }
}
