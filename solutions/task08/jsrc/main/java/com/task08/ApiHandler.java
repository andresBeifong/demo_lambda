package com.task08;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.lambda.LambdaLayer;
import com.syndicate.deployment.annotations.lambda.LambdaUrlConfig;
import com.syndicate.deployment.model.ArtifactExtension;
import com.syndicate.deployment.model.DeploymentRuntime;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.model.lambda.url.AuthType;
import com.syndicate.deployment.model.lambda.url.InvokeMode;
import com.task08.lambdalayer.OpenMeteoAPI;

import java.util.Map;

@LambdaHandler(
        lambdaName = "api_handler",
        roleName = "api_handler-role",
        isPublishVersion = true,
        aliasName = "${lambdas_alias_name}",
        logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED,
        layers = {"sdk_layer"}
)
@LambdaLayer(
        layerName = "sdk_layer",
        runtime = DeploymentRuntime.JAVA11,
        artifactExtension = ArtifactExtension.ZIP,
        libraries = "lib/open-meteo-service-1.0-SNAPSHOT.jar"
)@LambdaUrlConfig(
        authType = AuthType.NONE,
        invokeMode = InvokeMode.BUFFERED
)
public class ApiHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {
    private final OpenMeteoAPI weatherAPI = new OpenMeteoAPI();
    private final Map<String, String> responseHeaders = Map.of("Content-Type", "application/json");

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        APIGatewayV2HTTPEvent.RequestContext.Http http = event.getRequestContext().getHttp();
        if("GET".equals(http.getMethod()) && http.getPath().contains("/weather")){
            String weatherData = weatherAPI.getData();
            return APIGatewayV2HTTPResponse.builder()
                    .withBody(weatherData)
                    .withStatusCode(200)
                    .withHeaders(responseHeaders)
                    .build();
        }
        return APIGatewayV2HTTPResponse.builder()
                .withBody("Path not found")
                .withStatusCode(404)
                .build();
    }
}
