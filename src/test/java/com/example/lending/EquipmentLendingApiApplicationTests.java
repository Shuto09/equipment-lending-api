package com.example.lending;

import com.example.lending.support.AppDbTest;
import org.junit.jupiter.api.Test;

/** アプリ全体の文脈が本物の PostgreSQL（Flyway 適用済み）で起動する。 */
@AppDbTest
class EquipmentLendingApiApplicationTests {

	@Test
	void contextLoads() {
	}

}
