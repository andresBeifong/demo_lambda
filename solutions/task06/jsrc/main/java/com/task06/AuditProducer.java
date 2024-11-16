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
		String targetTable = System.getenv("target_table");
		if("INSERT".equals(record.getEventName())){
			logger.log("Inserting new item: " + record.getDynamodb().getNewImage());
			insertItem(record, targetTable, logger);
        }else if("MODIFY".equals(record.getEventName())){
			logger.log("Updating item: " + record.getDynamodb().getNewImage());
			updateItem(record, targetTable, logger);
		}
	}

	private void insertItem(DynamodbEvent.DynamodbStreamRecord record, String tableName, LambdaLogger logger){
		Map<String, AttributeValue> configurationMap = record.getDynamodb().getNewImage();
		Map<String, software.amazon.awssdk.services.dynamodb.model.AttributeValue> newAuditMap = new HashMap<>();
		Map<String, software.amazon.awssdk.services.dynamodb.model.AttributeValue> newValueMap = new HashMap<>();

		newValueMap.put("key",
				software.amazon.awssdk.services.dynamodb.model.AttributeValue.builder()
						.s(configurationMap.get("key").getS())
						.build()
		);
		newValueMap.put("value",
				software.amazon.awssdk.services.dynamodb.model.AttributeValue.builder()
						.n(configurationMap.get("value").getN())
						.build()
		);

		newAuditMap.put("id", software.amazon.awssdk.services.dynamodb.model.AttributeValue.builder().s(UUID.randomUUID().toString()).build());
		newAuditMap.put("itemKey", software.amazon.awssdk.services.dynamodb.model.AttributeValue.builder().s(configurationMap.get("key").getS()).build());
		newAuditMap.put("modificationTime", software.amazon.awssdk.services.dynamodb.model.AttributeValue.builder().s(ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)).build());
		newAuditMap.put("newValue", software.amazon.awssdk.services.dynamodb.model.AttributeValue.builder().m(newValueMap).build());

		PutItemRequest auditItemRequest = PutItemRequest.builder().tableName(tableName).item(newAuditMap).build();
		try {
			dynamoDB.putItem(auditItemRequest);
		} catch (DynamoDbException ex) {
			logger.log("An error occurred when saving Audit to DynamoDB: " + ex.getMessage());
			throw ex;
		}
	}

	private void updateItem(DynamodbEvent.DynamodbStreamRecord record, String tableName, LambdaLogger logger){
		Map<String, AttributeValue> configurationData = record.getDynamodb().getNewImage();
		Map<String, software.amazon.awssdk.services.dynamodb.model.AttributeValue> currentAudit =
				getItem(tableName, "item_key_index", "itemKey", configurationData.get("key").getS(), logger);

		if(currentAudit != null) {
			Map<String, AttributeValue> oldConfigurationData = record.getDynamodb().getOldImage();
			HashMap<String, software.amazon.awssdk.services.dynamodb.model.AttributeValue> itemKey = new HashMap<>();

			itemKey.put("id", currentAudit.get("id"));

			HashMap<String, AttributeValueUpdate> updatedValues = new HashMap<>();

			updatedValues.put("modificationTime", AttributeValueUpdate.builder()
					.value(software.amazon.awssdk.services.dynamodb.model.AttributeValue.builder().s(ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)).build())
					.action(AttributeAction.PUT)
					.build());
			updatedValues.put("oldValue", AttributeValueUpdate.builder()
					.value(software.amazon.awssdk.services.dynamodb.model.AttributeValue.builder().s(oldConfigurationData.get("value").getN()).build())
					.action(AttributeAction.PUT)
					.build());
			updatedValues.put("newValue", AttributeValueUpdate.builder()
					.value(software.amazon.awssdk.services.dynamodb.model.AttributeValue.builder().s(configurationData.get("value").getN()).build())
					.action(AttributeAction.PUT)
					.build());
			updatedValues.put("updatedAttribute", AttributeValueUpdate.builder()
					.value(software.amazon.awssdk.services.dynamodb.model.AttributeValue.builder().s("value").build())
					.action(AttributeAction.PUT)
					.build());

			UpdateItemRequest updateItemRequest = UpdateItemRequest.builder()
					.tableName(tableName)
					.key(itemKey)
					.attributeUpdates(updatedValues)
					.build();
			try {
				dynamoDB.updateItem(updateItemRequest);
			} catch (DynamoDbException ex) {
				logger.log("An error occurred when updating Audit to DynamoDB: " + ex.getMessage());
				throw ex;
			}
		}
	}

	private Map<String, software.amazon.awssdk.services.dynamodb.model.AttributeValue> getItem(String tableName, String indexName, String key, String keyVal, LambdaLogger logger) {
		logger.log("Getting item by key: " + key + " value: " + keyVal);

		Map<String, software.amazon.awssdk.services.dynamodb.model.AttributeValue> values = new HashMap<>();
		values.put(":itemKey",
				software.amazon.awssdk.services.dynamodb.model.AttributeValue.builder()
						.s(keyVal)
						.build()
		);

		QueryRequest queryRequest = QueryRequest.builder()
				.tableName(tableName)
				.indexName(indexName)
				.keyConditionExpression("itemKey = :itemKey")
				.expressionAttributeValues(values)
				.build();

		try {
			QueryResponse response = dynamoDB.query(queryRequest);
			if(!response.items().isEmpty()){
				return response.items().get(0);
			}
		} catch (DynamoDbException e) {
			logger.log("An error occurred while getting item: " + e.getMessage());
		}
		return null;
	}
}
