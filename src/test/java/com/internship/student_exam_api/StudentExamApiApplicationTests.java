package com.internship.student_exam_api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class StudentExamApiApplicationTests {

	@Test
	void simpleMathTest() {
		int result = 2 + 2;

		org.junit.jupiter.api.Assertions.assertEquals(4, result);
	}

}
