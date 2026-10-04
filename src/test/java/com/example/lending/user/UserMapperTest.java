package com.example.lending.user;

import com.example.lending.support.MapperDbTest;
import com.example.lending.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/** 認証（AuthenticationInterceptor）が使う利用者の検索。 */
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

}
