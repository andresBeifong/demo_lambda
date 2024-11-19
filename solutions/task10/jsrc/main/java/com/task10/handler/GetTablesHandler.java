package com.task10.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.task10.ApiHandler;
import org.json.JSONArray;
import org.json.JSONObject;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

import java.util.List;
import java.util.Map;

public class GetTablesHandler  implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent requestEvent, Context context) {
        try {
            ScanRequest scanRequest = ScanRequest.builder().tableName(ApiHandler.TABLES_TABLE_NAME).build();
            ScanResponse response = ApiHandler.dynamoDB.scan(scanRequest);
            List<Map<String, AttributeValue>> items = response.items();
            JSONArray tables = new JSONArray();

            for(Map<String, AttributeValue> item : items){
                JSONObject tableJson = new JSONObject();
                tableJson.put("id", item.get("id").getValueForField("N", Integer.class));
                tableJson.put("number", item.get("number").getValueForField("N", Integer.class));
                tableJson.put("places", item.get("places").getValueForField("N", Integer.class));
                tableJson.put("isVip", item.get("isVip").getValueForField("B", Boolean.class));
                if(item.containsKey("minOrder"))
                    tableJson.put("minOrder", item.get("minOrder").getValueForField("N", Integer.class));
                tables.put(tableJson);
            }
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withBody(new JSONObject().put("tables", tables).toString());
        } catch (DynamoDbException e) {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withBody(new JSONObject().put("error", e.getMessage()).toString());
        }
    }
}