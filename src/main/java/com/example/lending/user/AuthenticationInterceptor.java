package com.example.lending.user;

import java.util.regex.Pattern;

import com.example.lending.shared.error.BusinessException;
import com.example.lending.shared.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * デモ方式の認証。/api/** の全要求で X-User-Id ヘッダを検証し、{@link CurrentUser} を request 属性に置く。
 * ハンドラの引数（{@code @Valid @RequestBody} など）を解決する前に動くので、401 は 400 より先に返る。
 * 誰でも他人の ID を名乗れるので本番では使えない。本番では Spring Security のフィルタチェーン（OIDC）に置き換える。
 */
@Component
public class AuthenticationInterceptor implements HandlerInterceptor {

	public static final String HEADER = "X-User-Id";

	static final String ATTRIBUTE = CurrentUser.class.getName();

	private static final Pattern USER_ID = Pattern.compile("[0-9]{1,18}");

	private final UserMapper userMapper;

	public AuthenticationInterceptor(UserMapper userMapper) {
		this.userMapper = userMapper;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		String header = request.getHeader(HEADER);
		if (header == null) {
			throw unauthenticated(HEADER + " ヘッダがありません");
		}
		if (!USER_ID.matcher(header).matches()) {
			throw unauthenticated(HEADER + " ヘッダは数字で指定してください");
		}
		CurrentUser user = userMapper.findById(Long.parseLong(header))
			.map(row -> new CurrentUser(row.id(), row.role()))
			.orElseThrow(() -> unauthenticated(HEADER + " ヘッダの利用者が見つかりません"));
		request.setAttribute(ATTRIBUTE, user);
		return true;
	}

	private static BusinessException unauthenticated(String detail) {
		return new BusinessException(ErrorCode.UNAUTHENTICATED, detail);
	}

}
