package com.ergowmr.taskmanager.tasks;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

@SpringBootApplication
public class TasksServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(TasksServiceApplication.class, args);
	}

	@Bean
	RestClient usersRestClient(@Value("${users-service.url}") String baseUrl) {
		return RestClient.builder().baseUrl(baseUrl).build();
	}

}
