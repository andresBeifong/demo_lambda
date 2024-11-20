package com.theTask11.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.theTask11.ApiHandler;
import org.json.JSONObject;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;

import java.util.HashMap;
import java.util.Map;

public class GetTableByIdHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent requestEvent, Context context) {
        try {
            Map<String, AttributeValue> keyToGet = new HashMap<>();
            Map<String, String> pathParameters = requestEvent.getPathParameters();
            String tableId = pathParameters.get("tableId");
            if(tableId == null){
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withBody(new JSONObject().put("error", "The is id not found in query parameters").toString());
            }

            keyToGet.put("id", AttributeValue.builder()
                    .n(tableId)
                    .build());

            GetItemRequest request = GetItemRequest.builder()
                    .key(keyToGet)
                    .tableName(ApiHandler.TABLES_TABLE_NAME)
                    .build();

            Map<String, AttributeValue> found = ApiHandler.dynamoDB.getItem(request).item();
            if(found != null && !found.isEmpty()) {
                JSONObject tableJson = new JSONObject();
                tableJson.put("id", Integer.parseInt(found.get("id").n()));
                tableJson.put("number", Integer.parseInt(found.get("tableNumber").n()));
                tableJson.put("places", Integer.parseInt(found.get("places").n()));
                tableJson.put("isVip", found.get("isVip").bool());
                if(found.containsKey("minOrder"))
                    tableJson.put("minOrder", Integer.parseInt(found.get("minOrder").n()));
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(200)
                        .withBody(tableJson.toString());
            }
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withBody(new JSONObject().put("error", "Element not found").toString());
        } catch (DynamoDbException e) {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withBody(new JSONObject().put("error", e.getMessage()).toString());
        }
    }
}
