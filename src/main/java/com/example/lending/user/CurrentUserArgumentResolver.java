package com.example.lending.user;

import com.example.lending.shared.error.BusinessException;
import com.example.lending.shared.error.ErrorCode;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** コントローラの {@link CurrentUser} 引数に、{@link AuthenticationInterceptor} が置いた利用者を渡す（読むだけ）。 */
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.getParameterType() == CurrentUser.class;
	}

	@Override
	public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
		if (webRequest.getAttribute(AuthenticationInterceptor.ATTRIBUTE,
				RequestAttributes.SCOPE_REQUEST) instanceof CurrentUser user) {
			return user;
		}
		// interceptor が掛かっていないパスで CurrentUser を受けようとした（設定の誤り）
		throw new BusinessException(ErrorCode.UNAUTHENTICATED, AuthenticationInterceptor.HEADER + " ヘッダがありません");
	}

}
