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

public class GetReservationsHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent requestEvent, Context context) {
        try {
            ScanRequest scanRequest = ScanRequest.builder().tableName(ApiHandler.RESERVATIONS_TABLE_NAME).build();
            ScanResponse response = ApiHandler.dynamoDB.scan(scanRequest);
            List<Map<String, AttributeValue>> items = response.items();
            JSONArray reservations = new JSONArray();

            for(Map<String, AttributeValue> item : items){
                JSONObject tableJson = new JSONObject();
                tableJson.put("tableNumber", Integer.parseInt(item.get("tableNumber").n()));
                tableJson.put("clientName", item.get("clientName").s());
                tableJson.put("phoneNumber", item.get("phoneNumber").s());
                tableJson.put("date", item.get("reservationDate").s());
                tableJson.put("slotTimeStart", item.get("slotTimeStart").s());
                tableJson.put("slotTimeEnd", item.get("slotTimeEnd").s());
                reservations.put(tableJson);
            }
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withBody(new JSONObject().put("reservations", reservations).toString());
        } catch (DynamoDbException e) {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withBody(new JSONObject().put("error", e.getMessage()).toString());
        }
    }
}
