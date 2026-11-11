package com.expense.calculator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ExpenseCalculatorApplication {

	public static void main(String[] args) {
		SpringApplication.run(ExpenseCalculatorApplication.class, args);
	}

}
