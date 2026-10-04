package com.example.lending.support;

import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;

import org.springframework.dao.DuplicateKeyException;

/** 単体テスト用に、PostgreSQL が返すのと同じ形の例外を作る。 */
public final class PostgresErrors {

	private PostgresErrors() {
	}

	/** 一意制約違反（SQLSTATE 23505・制約名つき）。DbConstraints は制約名をここから取り出す。 */
	public static DuplicateKeyException duplicateKey(String constraint) {
		String fields = String.join("\0", "SERROR", "C23505", "Mduplicate key value violates unique constraint",
				"n" + constraint) + "\0";
		return new DuplicateKeyException("duplicate key", new PSQLException(new ServerErrorMessage(fields)));
	}

}
