package com.task08;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.lambda.LambdaLayer;
import com.syndicate.deployment.model.ArtifactExtension;
import com.syndicate.deployment.model.DeploymentRuntime;
import com.syndicate.deployment.model.RetentionSetting;
import com.task08.lambdalayer.OpenMeteoAPI;

import java.io.IOException;

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
)
public class ApiHandler implements RequestHandler<Object, String> {
    private final OpenMeteoAPI weatherAPI = new OpenMeteoAPI();

    public String handleRequest(Object request, Context context) {
        try {
            return weatherAPI.getData();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
