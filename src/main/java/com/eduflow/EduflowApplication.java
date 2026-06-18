package com.eduflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class EduflowApplication {

	public static void main(String[] args) {
		SpringApplication.run(EduflowApplication.class, args);
	}

}
