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

public class PostTablesHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent requestEvent, Context context) {
        try {
            JSONObject tableRequest = new JSONObject(requestEvent.getBody());
            Map<String, AttributeValue> tableDataMap = new HashMap<>();
            int tableId = (int) tableRequest.get("id");
            tableDataMap.put("id", AttributeValue.builder().n(String.valueOf(tableId)).build());
            tableDataMap.put("tableNumber", AttributeValue.builder().n(String.valueOf(tableRequest.get("number"))).build());
            tableDataMap.put("places", AttributeValue.builder().n(String.valueOf(tableRequest.get("places"))).build());
            tableDataMap.put("isVip", AttributeValue.builder().bool((Boolean) tableRequest.get("isVip")).build());
            if(tableRequest.has("minOrder"))
                tableDataMap.put("minOrder", AttributeValue.builder().n(String.valueOf(tableRequest.get("minOrder"))).build());

            PutItemRequest putItemRequest = PutItemRequest.builder().tableName(ApiHandler.TABLES_TABLE_NAME).item(tableDataMap).build();

            ApiHandler.dynamoDB.putItem(putItemRequest);

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withBody(new JSONObject().put("id", tableId).toString());
        } catch (DynamoDbException e) {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withBody(new JSONObject().put("error", e.getMessage()).toString());
        }
    }
}
