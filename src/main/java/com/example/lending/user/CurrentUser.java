package com.example.lending.user;

/**
 * 要求を出した利用者。サービスはこれだけを知り、認証の方式（今はデモの X-User-Id）を知らない。
 * 本番で Spring Security＋OIDC に置き換えても、サービスは変わらない（docs/要件と設計.md §12）。
 */
public record CurrentUser(long id, Role role) {

	public boolean isAdmin() {
		return role == Role.ADMIN;
	}

}
