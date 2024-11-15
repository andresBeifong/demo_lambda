package com.task06;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.events.DynamoDbTriggerEventSource;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.RetentionSetting;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@LambdaHandler(
    lambdaName = "audit_producer",
	roleName = "audit_producer-role",
	isPublishVersion = true,
	aliasName = "${lambdas_alias_name}",
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@DynamoDbTriggerEventSource(targetTable = "Configuration", batchSize = 10)
@EnvironmentVariable(key = "target_table", value = "${target_table}")
public class AuditProducer implements RequestHandler<DynamodbEvent , Void> {
	private static final DynamoDbClient dynamoDB = DynamoDbClient.builder().region(Region.EU_CENTRAL_1).build();

	public Void handleRequest(DynamodbEvent  event, Context context) {
		LambdaLogger logger = context.getLogger();
		for(DynamodbEvent.DynamodbStreamRecord record : event.getRecords()){
			handleRecordEvent(record, logger);
		}
		return null;
	}

	private void handleRecordEvent(DynamodbEvent.DynamodbStreamRecord record, LambdaLogger logger){
		Map<String, AttributeValue> configurationData = record.getDynamodb().getNewImage();
		String targetTable = System.getenv("target_table");
		if("INSERT".equals(record.getEventName())){
			Map<String, software.amazon.awssdk.services.dynamodb.model.AttributeValue> auditMap = new HashMap<>();
			Map<String, software.amazon.awssdk.services.dynamodb.model.AttributeValue> valueMap = new HashMap<>();
			valueMap.put("key", software.amazon.awssdk.services.dynamodb.model.AttributeValue.builder().s(String.valueOf(configurationData.get("key"))).build());
			valueMap.put("value", software.amazon.awssdk.services.dynamodb.model.AttributeValue.builder().n(String.valueOf(configurationData.get("value"))).build());

			auditMap.put("id", software.amazon.awssdk.services.dynamodb.model.AttributeValue.builder().s(UUID.randomUUID().toString()).build());
			auditMap.put("itemKey", software.amazon.awssdk.services.dynamodb.model.AttributeValue.builder().s(String.valueOf(configurationData.get("key"))).build());
			auditMap.put("modificationTime", software.amazon.awssdk.services.dynamodb.model.AttributeValue.builder().s(ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)).build());
			auditMap.put("newValue", software.amazon.awssdk.services.dynamodb.model.AttributeValue.builder().m(valueMap).build());

			PutItemRequest auditItemRequest = PutItemRequest.builder().tableName(targetTable).item(auditMap).build();
            try {
                dynamoDB.putItem(auditItemRequest);
            } catch (DynamoDbException ex) {
				logger.log("An error occurred when saving Audit to DynamoDB: " + ex.getMessage());
				throw ex;
			}
        }else if("MODIFY".equals(record.getEventName())){
			Map<String, AttributeValue> oldConfigurationData = record.getDynamodb().getOldImage();
			HashMap<String, software.amazon.awssdk.services.dynamodb.model.AttributeValue> itemKey = new HashMap<>();
			itemKey.put("id", software.amazon.awssdk.services.dynamodb.model.AttributeValue.builder().s(String.valueOf(configurationData.get("id"))).build());

			HashMap<String, AttributeValueUpdate> updatedValues = new HashMap<>();

			updatedValues.put("modificationTime", AttributeValueUpdate.builder()
					.value(software.amazon.awssdk.services.dynamodb.model.AttributeValue.builder().s(ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)).build())
					.action(AttributeAction.PUT)
					.build());
			updatedValues.put("oldValue", AttributeValueUpdate.builder()
					.value(software.amazon.awssdk.services.dynamodb.model.AttributeValue.builder().s(String.valueOf(oldConfigurationData.get("value"))).build())
					.action(AttributeAction.PUT)
					.build());
			updatedValues.put("newValue", AttributeValueUpdate.builder()
					.value(software.amazon.awssdk.services.dynamodb.model.AttributeValue.builder().s(String.valueOf(configurationData.get("value"))).build())
					.action(AttributeAction.PUT)
					.build());
			updatedValues.put("updatedAttribute", AttributeValueUpdate.builder()
					.value(software.amazon.awssdk.services.dynamodb.model.AttributeValue.builder().s("value").build())
					.action(AttributeAction.PUT)
					.build());

			UpdateItemRequest updateItemRequest = UpdateItemRequest.builder().tableName(targetTable).key(itemKey).attributeUpdates(updatedValues).build();
			try {
				dynamoDB.updateItem(updateItemRequest);
			} catch (DynamoDbException ex) {
				logger.log("An error occurred when updating Audit to DynamoDB: " + ex.getMessage());
				throw ex;
			}
		}
	}
}