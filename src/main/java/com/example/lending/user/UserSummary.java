package com.example.lending.user;

/** 応答に載せる利用者。全員に見せてよいのは表示名まで（メールアドレスは延滞一覧だけ）。 */
public record UserSummary(long id, String displayName) {

}
