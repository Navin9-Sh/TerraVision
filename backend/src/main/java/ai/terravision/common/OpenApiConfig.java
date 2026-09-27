package ai.terravision.common;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@SecurityScheme(name = "ApiKeyAuth", type = SecuritySchemeType.APIKEY, in = SecuritySchemeIn.HEADER, paramName = "X-API-Key")
public class OpenApiConfig {

    @Bean
    public OpenAPI terraVisionOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("TerraVision API")
                        .version("v1")
                        .description("Satellite land-use classification API. Fine-tuned ResNet50 on the "
                                + "EuroSAT (Sentinel-2) dataset, served via Deep Java Library's PyTorch engine."))
                .addSecurityItem(new SecurityRequirement().addList("ApiKeyAuth"));
    }
}
