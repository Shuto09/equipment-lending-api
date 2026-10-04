package com.example.lending.shared.sql;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;

/**
 * DB の制約名（V1__create_tables.sql と一致させる）と、例外から「どの制約に違反したか」を取り出す 1 か所。
 * 一意制約違反は制約名で業務エラーに変換し、知らない制約なら変換しない（原因を取り違えないため 500 にする）。
 */
public final class DbConstraints {

	public static final String UQ_USERS_EMAIL = "uq_users_email";

	public static final String CK_USERS_ROLE = "ck_users_role";

	public static final String UQ_EQUIPMENT_ASSET_CODE = "uq_equipment_asset_code";

	public static final String CK_EQUIPMENT_CATEGORY = "ck_equipment_category";

	public static final String CK_EQUIPMENT_STATUS = "ck_equipment_status";

	public static final String CK_EQUIPMENT_DISPOSED = "ck_equipment_disposed";

	public static final String CK_EQUIPMENT_REMARKS_NOT_EMPTY = "ck_equipment_remarks_not_empty";

	public static final String UQ_LOANS_OPEN_EQUIPMENT = "uq_loans_open_equipment";

	public static final String CK_LOANS_RETURNED_AFTER_LENT = "ck_loans_returned_after_lent";

	private DbConstraints() {
	}

	/** 例外の原因をたどり、PostgreSQL が返した制約名を取り出す。 */
	public static Optional<String> violatedConstraint(Throwable exception) {
		Set<Throwable> seen = new HashSet<>();
		for (Throwable cause = exception; cause != null && seen.add(cause); cause = cause.getCause()) {
			if (cause instanceof PSQLException psql) {
				ServerErrorMessage message = psql.getServerErrorMessage();
				return Optional.ofNullable(message).map(ServerErrorMessage::getConstraint);
			}
		}
		return Optional.empty();
	}

	public static boolean isViolationOf(Throwable exception, String constraintName) {
		return violatedConstraint(exception).filter(constraintName::equals).isPresent();
	}

}
