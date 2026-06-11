package com.internship.student_exam_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StudentExamApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(StudentExamApiApplication.class, args);
	}

}
