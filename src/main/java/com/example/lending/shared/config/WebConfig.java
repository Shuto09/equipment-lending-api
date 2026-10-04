package com.example.lending.shared.config;

import java.util.List;

import com.example.lending.user.AuthenticationInterceptor;
import com.example.lending.user.CurrentUserArgumentResolver;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 認証（デモ方式）を /api/** に掛ける。interceptor はハンドラの引数を解決する前に動くので、
 * 401 は入力検証の 400 より先に返る（docs/要件と設計.md §12）。
 */
@Configuration(proxyBeanMethods = false)
public class WebConfig implements WebMvcConfigurer {

	private final AuthenticationInterceptor authenticationInterceptor;

	private final CurrentUserArgumentResolver currentUserArgumentResolver;

	public WebConfig(AuthenticationInterceptor authenticationInterceptor,
			CurrentUserArgumentResolver currentUserArgumentResolver) {
		this.authenticationInterceptor = authenticationInterceptor;
		this.currentUserArgumentResolver = currentUserArgumentResolver;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(authenticationInterceptor).addPathPatterns("/api/**");
	}

	@Override
	public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
		resolvers.add(currentUserArgumentResolver);
	}

}
