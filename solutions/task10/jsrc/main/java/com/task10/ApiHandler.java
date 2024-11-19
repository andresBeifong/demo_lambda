package com.task10;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.resources.DependsOn;
import com.syndicate.deployment.model.ResourceType;
import com.syndicate.deployment.model.RetentionSetting;
import com.task10.dto.RouteKey;
import com.task10.handler.*;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;

import java.util.Map;

import static com.syndicate.deployment.model.environment.ValueTransformer.USER_POOL_NAME_TO_CLIENT_ID;
import static com.syndicate.deployment.model.environment.ValueTransformer.USER_POOL_NAME_TO_USER_POOL_ID;

@LambdaHandler(
    lambdaName = "api_handler",
	roleName = "api_handler-role",
	isPublishVersion = true,
	aliasName = "${lambdas_alias_name}",
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@DependsOn(resourceType = ResourceType.COGNITO_USER_POOL, name = "${booking_userpool}")
@EnvironmentVariables(value = {
		@EnvironmentVariable(key = "REGION", value = "${region}"),
		@EnvironmentVariable(key = "COGNITO_ID", value = "${pool_name}", valueTransformer = USER_POOL_NAME_TO_USER_POOL_ID),
		@EnvironmentVariable(key = "CLIENT_ID", value = "${pool_name}", valueTransformer = USER_POOL_NAME_TO_CLIENT_ID)
})
public class ApiHandler implements  RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

	private final CognitoIdentityProviderClient cognitoClient;
	private final Map<RouteKey, RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent>> handlersByRouteKey;
	private final Map<String, String> headersForCORS;
	private final RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> routeNotImplementedHandler;

	public ApiHandler() {
		this.cognitoClient = initCognitoClient();
		this.handlersByRouteKey = initHandlers();
		this.headersForCORS = initHeadersForCORS();
		this.routeNotImplementedHandler = new RouteNotImplementedHandler();
	}

	@Override
	public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent requestEvent, Context context) {
		return getHandler(requestEvent)
				.handleRequest(requestEvent, context)
				.withHeaders(headersForCORS);
	}

	private RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> getHandler(APIGatewayProxyRequestEvent requestEvent) {
		return handlersByRouteKey.getOrDefault(getRouteKey(requestEvent), routeNotImplementedHandler);
	}

	private RouteKey getRouteKey(APIGatewayProxyRequestEvent requestEvent) {
		return new RouteKey(requestEvent.getHttpMethod(), requestEvent.getPath());
	}

	private CognitoIdentityProviderClient initCognitoClient() {
		return CognitoIdentityProviderClient.builder()
				.region(Region.of(System.getenv("REGION")))
				.credentialsProvider(DefaultCredentialsProvider.create())
				.build();
	}

	private Map<RouteKey, RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent>> initHandlers() {
		return Map.of(
				new RouteKey("GET", "/"), new GetRootHandler(),
				new RouteKey("POST", "/signup"), new PostSignUpHandler(cognitoClient),
				new RouteKey("POST", "/signin"), new PostSignInHandler(cognitoClient),
				new RouteKey("GET", "/tables"), new GetTablesHandler()
		);
	}

	/**
	 * To allow all origins, all methods, and common headers
	 * <a href="https://docs.aws.amazon.com/apigateway/latest/developerguide/how-to-cors.html">Using cross-origin resource sharing (CORS)</a>
	 */
	private Map<String, String> initHeadersForCORS() {
		return Map.of(
				"Access-Control-Allow-Headers", "Content-Type,X-Amz-Date,Authorization,X-Api-Key,X-Amz-Security-Token",
				"Access-Control-Allow-Origin", "*",
				"Access-Control-Allow-Methods", "*",
				"Accept-Version", "*"
		);
	}
}
