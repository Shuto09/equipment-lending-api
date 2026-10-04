package com.example.lending;

import org.springframework.boot.SpringApplication;

public class TestEquipmentLendingApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(EquipmentLendingApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
