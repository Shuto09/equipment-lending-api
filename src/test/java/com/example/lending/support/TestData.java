package com.example.lending.support;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import javax.sql.DataSource;

import com.example.lending.equipment.EquipmentCategory;
import com.example.lending.user.Role;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * DB を使うテストの行を JdbcClient で直接入れる補助（docs/テスト計画.md §7）。データはすべて架空。
 * マッパー（DB）のテストではテストのトランザクションに参加するので、入れた行はテストの後にロールバックされる。
 */
public class TestData {

	/** 基準の時刻: 2026-10-05 10:00 日本時間。 */
	public static final OffsetDateTime BASE = OffsetDateTime.of(2026, 10, 5, 10, 0, 0, 0, ZoneOffset.ofHours(9));

	public static final Instant BASE_TIME = BASE.toInstant();

	/** 基準の時刻の業務日付（2026-10-05）。 */
	public static final LocalDate TODAY = BASE.toLocalDate();

	private final JdbcClient jdbc;

	private final MutableClock clock;

	public TestData(DataSource dataSource, ObjectProvider<MutableClock> clock) {
		this.jdbc = JdbcClient.create(dataSource);
		this.clock = clock.getIfAvailable();
	}

	/** COMMIT するテストの前に呼ぶ: 3 表を空にし、時計を基準の時刻に戻す。 */
	public void reset() {
		deleteAll();
		if (clock != null) {
			clock.set(BASE_TIME);
		}
	}

	/** 3 表を空にする（マッパーのテストではテストのトランザクションの中なので、終わればロールバックされる）。 */
	public void deleteAll() {
		jdbc.sql("TRUNCATE loans, equipment, users RESTART IDENTITY CASCADE").update();
	}

	public JdbcClient jdbc() {
		return jdbc;
	}

	public long user(String displayName, String email, Role role) {
		return jdbc.sql("INSERT INTO users (display_name, email, role) VALUES (?, ?, ?) RETURNING id")
			.params(displayName, email, role.name())
			.query(Long.class)
			.single();
	}

	public long admin() {
		return user("鈴木 一郎", "suzuki@example.com", Role.ADMIN);
	}

	public long yamada() {
		return user("山田 太郎", "yamada@example.com", Role.MEMBER);
	}

	public long sato() {
		return user("佐藤 花子", "sato@example.com", Role.MEMBER);
	}

	public long equipment(String assetCode, String name, EquipmentCategory category) {
		return equipment(assetCode, name, category, BASE);
	}

	public long equipment(String assetCode, String name, EquipmentCategory category, OffsetDateTime createdAt) {
		return jdbc.sql("""
				INSERT INTO equipment (asset_code, name, category, created_at)
				VALUES (?, ?, ?, ?) RETURNING id""")
			.params(assetCode, name, category.name(), createdAt)
			.query(Long.class)
			.single();
	}

	public long disposedEquipment(String assetCode, String name, EquipmentCategory category) {
		return jdbc.sql("""
				INSERT INTO equipment (asset_code, name, category, status, disposed_at, created_at)
				VALUES (?, ?, ?, 'DISPOSED', ?, ?) RETURNING id""")
			.params(assetCode, name, category.name(), BASE.minusDays(1), BASE.minusDays(30))
			.query(Long.class)
			.single();
	}

	/** 未返却の貸出（貸出日時は基準の 7 日前）。 */
	public long openLoan(long equipmentId, long borrowerId, LocalDate dueDate) {
		return jdbc.sql("""
				INSERT INTO loans (equipment_id, borrower_id, lent_at, due_date)
				VALUES (?, ?, ?, ?) RETURNING id""")
			.params(equipmentId, borrowerId, BASE.minusDays(7), dueDate)
			.query(Long.class)
			.single();
	}

	public long returnedLoan(long equipmentId, long borrowerId, LocalDate dueDate, OffsetDateTime returnedAt) {
		return jdbc.sql("""
				INSERT INTO loans (equipment_id, borrower_id, lent_at, due_date, returned_at)
				VALUES (?, ?, ?, ?, ?) RETURNING id""")
			.params(equipmentId, borrowerId, BASE.minusDays(7), dueDate, returnedAt)
			.query(Long.class)
			.single();
	}

}
