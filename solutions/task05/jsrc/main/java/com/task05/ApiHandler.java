package com.task05;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.RetentionSetting;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@LambdaHandler(
        lambdaName = "api_handler",
        roleName = "api_handler-role",
        isPublishVersion = true,
        aliasName = "${lambdas_alias_name}",
        logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
public class ApiHandler implements RequestHandler<Map<String,Object>, Map<String, Object>> {
    private static final Gson gson = new Gson();
    private static final DynamoDbClient dynamoDB = DynamoDbClient.builder().region(Region.EU_CENTRAL_1).build();

    public Map<String, Object> handleRequest(Map<String,Object> request, Context context) {
        LambdaLogger logger = context.getLogger();
        logger.log("Request body: " + request.toString());

        EventDTO eventDTO = new EventDTO((Integer) request.get("principalId"), gson.fromJson((String) request.get("content"), Content.class));

        EventData eventData = new EventData();
        eventData.setPrincipalId(eventDTO.getPrincipalId());
        eventData.setId(UUID.randomUUID().toString());
        eventData.setCreatedAt(ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        eventData.setBody(eventDTO.getContent());

        Map<String, AttributeValue> eventItem = new HashMap<>();
        eventItem.put("id", AttributeValue.builder().s(eventData.getId()).build());
        eventItem.put("principalId", AttributeValue.builder().n(eventData.getPrincipalId().toString()).build());
        eventItem.put("body", toDynamoDBMap(eventData.getBody()));
        eventItem.put("createdAt", AttributeValue.builder().s(eventData.getCreatedAt()).build());

        PutItemRequest eventItemRequest = PutItemRequest.builder().tableName("cmtr-71b5c20d-Events-test").item(eventItem).build();

        try {
            dynamoDB.putItem(eventItemRequest);
        } catch (DynamoDbException ex) {
            logger.log("An error occurred when saving Event to DynamoDB: " + ex.getMessage());
            throw ex;
        }

        EventResponse eventResponse = new EventResponse(eventData, 201);


        String responseBody = gson.toJson(eventResponse);
        logger.log("Response JSON: " + responseBody);

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        response.setStatusCode(201);
        response.setBody(responseBody);
        response.setHeaders(headers);

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("statusCode", 201);
        resultMap.put("body", "{\r\n" + //
                "    \"statusCode\": 201,\r\n" + //
                "    \"event\": {\r\n" + //
                "        \"id\": \"" + eventData.getId() + "\",\r\n" + //
                "        \"principalId\": " + eventData.getPrincipalId() + ",\r\n" + //
                "        \"createdAt\": \""  + eventData.createdAt + "\",\r\n" + //
                "        \"body\": "+ gson.toJson(eventData.getBody()) +" \r\n" + //
                "    }  \r\n" + //
                "}");

        return resultMap;
    }

    private AttributeValue toDynamoDBMap(Content content) {
        Map<String, AttributeValue> contentMap = new HashMap<>();
        contentMap.put("name", AttributeValue.builder().s(content.getName()).build());
        contentMap.put("surname", AttributeValue.builder().s(content.getSurname()).build());
        return AttributeValue.builder().m(contentMap).build();
    }

    class EventDTO{
        private int principalId;
        private Content content;

        public EventDTO() {
        }

        public EventDTO(int principalId, Content content) {
            this.principalId = principalId;
            this.content = content;
        }

        public int getPrincipalId() {
            return principalId;
        }

        public void setPrincipalId(int principalId) {
            this.principalId = principalId;
        }

        public Content getContent() {
            return content;
        }

        public void setContent(Content content) {
            this.content = content;
        }
    }

    class EventData {
        private String id;
        private Integer principalId;
        private Content body;
        private String createdAt;

        public EventData() {
            this.id = UUID.randomUUID().toString();
            this.createdAt = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }

        public EventData(String id, Integer principalId, Content content, String createdAt) {
            this.id = id;
            this.principalId = principalId;
            this.body = content;
            this.createdAt = createdAt;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public Integer getPrincipalId() {
            return principalId;
        }

        public void setPrincipalId(Integer principalId) {
            this.principalId = principalId;
        }

        public Content getBody() {
            return body;
        }

        public void setBody(Content content) {
            this.body = content;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(String createdAt) {
            this.createdAt = createdAt;
        }
    }

    class Content {
        private String name;
        private String surname;

        public Content() {
        }

        public Content(String name, String surname) {
            this.name = name;
            this.surname = surname;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getSurname() {
            return surname;
        }

        public void setSurname(String surname) {
            this.surname = surname;
        }
    }

    class EventResponse{
        private EventData event;
        private int statusCode;

        public EventResponse(EventData event, int statusCode) {
            this.event = event;
            this.statusCode = statusCode;
        }

        public EventData getEvent() {
            return event;
        }

        public void setEvent(EventData event) {
            this.event = event;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public void setStatusCode(int statusCode) {
            this.statusCode = statusCode;
        }
    }
}
