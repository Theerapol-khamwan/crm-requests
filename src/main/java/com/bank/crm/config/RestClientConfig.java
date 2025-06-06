package com.bank.crm.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory; // <--- Import เพิ่ม
import org.springframework.web.client.RestTemplate;
import java.time.Duration;

@Configuration
public class RestClientConfig {

	@Bean
	public RestTemplate restTemplate(RestTemplateBuilder builder) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();

		requestFactory.setConnectTimeout(Duration.ofSeconds(10));
		requestFactory.setReadTimeout(Duration.ofSeconds(30));

		return builder.requestFactory(() -> requestFactory).build();

	}

}
