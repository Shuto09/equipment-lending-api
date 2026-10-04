package com.example.lending.shared.config;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 業務の設定（application.properties の {@code lending.*}）。
 *
 * @param zone 業務日付（返却期限・延滞の判定）のタイムゾーン
 * @param maxLoanDays 返却期限の上限（今日から何日後まで）
 * @param maxOpenLoansPerBorrower 1 人の未返却の上限
 */
@ConfigurationProperties("lending")
public record LendingProperties(ZoneId zone, int maxLoanDays, int maxOpenLoansPerBorrower) {

	/** その時刻の業務日付（日本時間の日付）。 */
	public LocalDate dateOf(Instant instant) {
		return LocalDate.ofInstant(instant, zone);
	}

	/** 保存・応答に使う日時。オフセットを業務のタイムゾーンにそろえる。 */
	public OffsetDateTime timeOf(Instant instant) {
		return instant.atZone(zone).toOffsetDateTime();
	}

	/** DB から読んだ日時（オフセットは接続の設定しだい）を業務のタイムゾーンにそろえる。 */
	public OffsetDateTime toZone(OffsetDateTime dateTime) {
		return dateTime == null ? null : timeOf(dateTime.toInstant());
	}

}
