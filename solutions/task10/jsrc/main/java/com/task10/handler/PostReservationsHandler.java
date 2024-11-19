package com.task10.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.task10.ApiHandler;
import org.json.JSONObject;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PostReservationsHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent requestEvent, Context context) {
        try {
            JSONObject tableRequest = new JSONObject(requestEvent.getBody());
            Map<String, AttributeValue> tableDataMap = new HashMap<>();
            String reservationId = UUID.randomUUID().toString();

            tableDataMap.put("reservationId", AttributeValue.builder().s(reservationId).build());
            tableDataMap.put("tableNumber", AttributeValue.builder().s(String.valueOf(tableRequest.get("tableNumber"))).build());
            tableDataMap.put("clientName", AttributeValue.builder().s(String.valueOf(tableRequest.get("clientName"))).build());
            tableDataMap.put("phoneNumber", AttributeValue.builder().s(String.valueOf(tableRequest.get("phoneNumber"))).build());
            tableDataMap.put("date", AttributeValue.builder().s(String.valueOf(tableRequest.get("date"))).build());
            tableDataMap.put("slotTimeStart", AttributeValue.builder().s(String.valueOf(tableRequest.get("slotTimeStart"))).build());
            tableDataMap.put("slotTimeEnd", AttributeValue.builder().s(String.valueOf(tableRequest.get("slotTimeEnd"))).build());

            PutItemRequest putItemRequest = PutItemRequest.builder().tableName(ApiHandler.RESERVATIONS_TABLE_NAME).item(tableDataMap).build();

            ApiHandler.dynamoDB.putItem(putItemRequest);

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withBody(new JSONObject().put("reservationId", reservationId).toString());
        } catch (DynamoDbException e) {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withBody(new JSONObject().put("error", e.getMessage()).toString());
        }
    }
}
