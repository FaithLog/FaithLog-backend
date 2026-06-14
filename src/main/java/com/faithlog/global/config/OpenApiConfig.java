package com.faithlog.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	@Bean
	public OpenAPI faithLogOpenApi() {
		String bearerAuth = "bearerAuth";

		return new OpenAPI()
			.info(new Info()
				.title("FaithLog Backend API")
				.version("v1")
				.description("FaithLog 교회/캠퍼스 운영 앱 백엔드 API"))
			.servers(List.of(new Server().url("/").description("Current host")))
			.components(new Components()
				.addSecuritySchemes(bearerAuth, new SecurityScheme()
					.name(bearerAuth)
					.type(SecurityScheme.Type.HTTP)
					.scheme("bearer")
					.bearerFormat("JWT")))
			.addSecurityItem(new SecurityRequirement().addList(bearerAuth));
	}
}
