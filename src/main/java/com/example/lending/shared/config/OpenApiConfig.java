package com.example.lending.shared.config;

import com.example.lending.user.AuthenticationInterceptor;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import org.springdoc.core.customizers.OperationCustomizer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI（/v3/api-docs・/swagger-ui.html）。全操作に X-User-Id ヘッダ（必須）を載せる。
 * CurrentUser の引数はコントローラで {@code @Parameter(hidden = true)} にして載せない。
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

	@Bean
	OpenAPI lendingOpenApi() {
		return new OpenAPI().info(new Info().title("社内備品の貸出管理 API")
			.version("v1")
			.description("サンプルの API。利用者は X-User-Id ヘッダで受けるデモ方式で、本番では使えない（docs/要件と設計.md §12）。"));
	}

	@Bean
	OperationCustomizer userIdHeader() {
		return (operation, handlerMethod) -> operation.addParametersItem(new HeaderParameter()
			.name(AuthenticationInterceptor.HEADER)
			.required(true)
			.description("利用者の ID（デモ方式の認証）")
			.schema(new IntegerSchema().format("int64")));
	}

}
