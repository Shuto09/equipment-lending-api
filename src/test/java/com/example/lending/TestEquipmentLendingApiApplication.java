package com.example.lending;

import com.example.lending.support.TestcontainersConfiguration;

import org.springframework.boot.SpringApplication;

/** 手元で Docker だけがあるときの起動口: Testcontainers の PostgreSQL につないでアプリを動かす。 */
public class TestEquipmentLendingApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(EquipmentLendingApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
