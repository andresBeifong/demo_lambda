package com.task10.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.task10.ApiHandler;
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

            Map<String, String> queryStringParameters = requestEvent.getQueryStringParameters();
            String tableId = queryStringParameters.get("id");
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
            if(found != null) {
                JSONObject tableJson = new JSONObject();
                tableJson.put("id", found.get("id").getValueForField("N", Integer.class));
                tableJson.put("number", found.get("number").getValueForField("N", Integer.class));
                tableJson.put("places", found.get("places").getValueForField("N", Integer.class));
                tableJson.put("isVip", found.get("isVip").getValueForField("B", Boolean.class));
                if(found.containsKey("minOrder"))
                    tableJson.put("minOrder", found.get("minOrder").getValueForField("N", Integer.class));
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
