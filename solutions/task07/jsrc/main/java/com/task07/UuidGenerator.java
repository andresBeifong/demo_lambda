package com.task07;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.events.RuleEventSource;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.RetentionSetting;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@LambdaHandler(
    lambdaName = "uuid_generator",
	roleName = "uuid_generator-role",
	isPublishVersion = true,
	aliasName = "${lambdas_alias_name}",
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@RuleEventSource(targetRule = "uuid_trigger")
@EnvironmentVariable(key = "target_bucket", value = "${target_bucket}")
public class UuidGenerator {
	private final String targetBucket = System.getenv("target_bucket");
	private final S3Client s3Client = S3Client.builder().region(Region.EU_CENTRAL_1).build();

	public void handleRequest(Context context) {
		String filename = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
		try{
			File file = File.createTempFile(filename, "txt");
			BufferedWriter writer = new BufferedWriter(new FileWriter(file));
			for(int index = 0; index < 10; index ++){
				writer.write(UUID.randomUUID().toString());
			}
			writer.close();

			PutObjectRequest putObjectRequest = PutObjectRequest.builder()
					.bucket(targetBucket)
					.key(filename + ".txt")
					.build();

			s3Client.putObject(putObjectRequest, RequestBody.fromFile(file));
			file.delete();
		}catch (IOException | S3Exception ex){
			context.getLogger().log("An error occurred while trying to upload file to S3: " + ex.getMessage());
		}finally {
			s3Client.close();
		}
	}
}
