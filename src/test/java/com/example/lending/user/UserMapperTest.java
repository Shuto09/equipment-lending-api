package com.example.lending.user;

import com.example.lending.support.MapperDbTest;
import com.example.lending.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/** 認証（AuthenticationInterceptor）と貸出（LoanService）が使う利用者の検索。 */
@MapperDbTest
class UserMapperTest {

	@Autowired
	private UserMapper mapper;

	@Autowired
	private TestData data;

	@Test
	@DisplayName("id で利用者と役割を引ける。無い id は空")
	void findById() {
		data.deleteAll();
		long admin = data.admin();

		assertThat(mapper.findById(admin))
			.contains(new UserRow(admin, "鈴木 一郎", "suzuki@example.com", Role.ADMIN));
		assertThat(mapper.findById(admin + 1)).isEmpty();
	}

	@Test
	@DisplayName("行ロック付きでも同じ行を引ける。無い id は空（貸出の前に利用者行をロックする）")
	void lockById() {
		data.deleteAll();
		long yamada = data.yamada();

		assertThat(mapper.lockById(yamada)).contains(new UserRow(yamada, "山田 太郎", "yamada@example.com", Role.MEMBER));
		assertThat(mapper.lockById(yamada + 1)).isEmpty();
	}

}
