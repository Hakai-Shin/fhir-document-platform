package com.example.fhir.document_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.example.fhir")
public class DocumentServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(DocumentServiceApplication.class, args);
	}

}
