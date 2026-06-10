package com.internship.student_exam_api.seeder;

import com.internship.student_exam_api.dto.request.ResultCreateRequest;
import com.internship.student_exam_api.entity.Exam;
import com.internship.student_exam_api.entity.Student;
import com.internship.student_exam_api.entity.Subject;
import com.internship.student_exam_api.enums.StudentPerformanceProfile;
import com.internship.student_exam_api.repository.ExamRepository;
import com.internship.student_exam_api.repository.StudentRepository;
import com.internship.student_exam_api.repository.SubjectRepository;
import com.internship.student_exam_api.service.ResultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datafaker.Faker;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import org.springframework.data.domain.PageRequest;

@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class DatabaseSeeder implements CommandLineRunner {

    private final StudentRepository studentRepository;
    private final SubjectRepository subjectRepository;
    private final ExamRepository examRepository;
    private final ResultService resultService;

    private enum Trend { IMPROVING, DECLINING, STABLE, VOLATILE }

    @Override
    public void run(String... args) throws Exception {
        if (resultService.getAllResults(PageRequest.of(0, 1)).hasContent()) {
            log.info("Results already exist. Skipping synthetic data generation.");
            return;
        }
        
        // Clean up previous partial seeds (e.g. students without results)
        if (studentRepository.count() > 0) {
            examRepository.deleteAll();
            subjectRepository.deleteAll();
            studentRepository.deleteAll();
        }

        log.info("Starting synthetic database seeding...");
        Faker faker = new Faker();
        Random random = new Random();

        // 1. Create Subjects
        List<Subject> subjects = createSubjects();
        
        // 2. Create Exams
        List<Exam> exams = createExams(subjects);
        
        // 3. Create Students (Sections A, B, C)
        List<StudentProfilePair> students = createStudents(faker, random);
        
        // 4. Generate Results
        int resultsCreated = generateResults(students, exams, random);

        log.info("""
            
            =========================================
            Seeding complete
            Students: {}
            Sections: 3
            Subjects: {}
            Exams: {}
            Results: {}
            =========================================
            """, students.size(), subjects.size(), exams.size(), resultsCreated);
    }

    private List<Subject> createSubjects() {
        List<Subject> subjects = new ArrayList<>();
        subjects.add(subjectRepository.save(new Subject("Mathematics", "MATH101", 100)));
        subjects.add(subjectRepository.save(new Subject("Physics", "PHYS101", 100)));
        subjects.add(subjectRepository.save(new Subject("Chemistry", "CHEM101", 100)));
        subjects.add(subjectRepository.save(new Subject("Computer Science", "CS101", 100)));
        subjects.add(subjectRepository.save(new Subject("English", "ENG101", 100)));
        log.info("Subjects created: {}", subjects.size());
        return subjects;
    }

    private List<Exam> createExams(List<Subject> subjects) {
        List<Exam> exams = new ArrayList<>();
        LocalDate baseDate = LocalDate.now().minusMonths(6);

        // 20 exams total, 4 per subject
        for (Subject subject : subjects) {
            exams.add(examRepository.save(new Exam("Unit Test 1", subject, baseDate)));
            exams.add(examRepository.save(new Exam("Mid Semester", subject, baseDate.plusMonths(2))));
            exams.add(examRepository.save(new Exam("Unit Test 2", subject, baseDate.plusMonths(4))));
            exams.add(examRepository.save(new Exam("End Semester", subject, baseDate.plusMonths(5))));
        }
        
        // Sort exams by date so our trend logic works over time
        exams.sort(Comparator.comparing(Exam::getExamDate));
        
        log.info("Exams created: {}", exams.size());
        return exams;
    }

    private List<StudentProfilePair> createStudents(Faker faker, Random random) {
        List<StudentProfilePair> pairs = new ArrayList<>();

        // Ensure we cover all 4 trends evenly
        Trend[] trends = {Trend.IMPROVING, Trend.DECLINING, Trend.STABLE, Trend.VOLATILE};

        for (int i = 1; i <= 10; i++) {
            // Sections A, B, C
            String section;
            if (i <= 3) section = "A";
            else if (i <= 6) section = "B";
            else section = "C";
            
            String rollPrefix = String.format("MCA%03d", i);
            String rollNumber = rollPrefix + "-" + section;
            
            String firstName = faker.name().firstName();
            String lastName = faker.name().lastName();
            String name = firstName + " " + lastName;
            String email = firstName.toLowerCase() + "." + lastName.toLowerCase() + rollPrefix.toLowerCase() + "@example.com";

            Student student = new Student(name, email, rollNumber, section);
            student = studentRepository.save(student);

            StudentPerformanceProfile profile = assignProfile(random);
            Trend trend = trends[i % 4];
            
            pairs.add(new StudentProfilePair(student, profile, trend));
        }
        log.info("Students created: {}", pairs.size());
        return pairs;
    }

    private StudentPerformanceProfile assignProfile(Random random) {
        int chance = random.nextInt(100);
        if (chance < 15) return StudentPerformanceProfile.TOP_PERFORMER;
        if (chance < 40) return StudentPerformanceProfile.ABOVE_AVERAGE;
        if (chance < 75) return StudentPerformanceProfile.AVERAGE;
        if (chance < 90) return StudentPerformanceProfile.WEAK;
        return StudentPerformanceProfile.AT_RISK;
    }

    private int generateResults(List<StudentProfilePair> pairs, List<Exam> exams, Random random) {
        int resultsCreated = 0;
        
        for (StudentProfilePair pair : pairs) {
            Student student = pair.student();
            StudentPerformanceProfile profile = pair.profile();
            Trend trend = pair.trend();
            
            double baseMean = getProfileMean(profile);
            
            // Generate between 10 and 20 results per student (total 100-200)
            int examsToTake = 10 + random.nextInt(11); 
            
            List<Exam> shuffledExams = new ArrayList<>(exams);
            Collections.shuffle(shuffledExams, random);
            List<Exam> selectedExams = shuffledExams.subList(0, examsToTake);
            // Sort selected exams chronologically for accurate trend modeling
            selectedExams.sort(Comparator.comparing(Exam::getExamDate));
            
            for (int i = 0; i < selectedExams.size(); i++) {
                Exam exam = selectedExams.get(i);
                
                double rawMark = baseMean;
                double progressRatio = (double) i / Math.max(1, selectedExams.size() - 1); // 0.0 to 1.0
                
                switch (trend) {
                    case IMPROVING -> rawMark += (progressRatio * 30) - 15; // Starts -15 below mean, ends +15 above
                    case DECLINING -> rawMark += (progressRatio * -30) + 15; // Starts +15 above mean, ends -15 below
                    case STABLE -> rawMark += random.nextGaussian() * 2; // Very tight variance
                    case VOLATILE -> rawMark += random.nextGaussian() * 15; // High variance
                }
                
                // Add some natural random noise for all profiles
                if (trend != Trend.STABLE && trend != Trend.VOLATILE) {
                    rawMark += random.nextGaussian() * 4;
                }
                
                // Clamp marks tightly between 45 and 98 to meet strict prompt requirements
                rawMark = Math.max(45.0, Math.min(98.0, rawMark));
                
                // Round to nearest 2 decimal places
                double finalMark = Math.round(rawMark * 100.0) / 100.0;
                
                ResultCreateRequest request = new ResultCreateRequest();
                request.setStudentId(student.getId());
                request.setExamId(exam.getId());
                request.setMarks(java.math.BigDecimal.valueOf(finalMark));
                
                resultService.createResult(request);
                resultsCreated++;
            }
        }
        return resultsCreated;
    }

    private double getProfileMean(StudentPerformanceProfile profile) {
        return switch (profile) {
            case TOP_PERFORMER -> 88.0;
            case ABOVE_AVERAGE -> 80.0;
            case AVERAGE -> 70.0;
            case WEAK -> 55.0;
            case AT_RISK -> 48.0;
        };
    }

    private record StudentProfilePair(Student student, StudentPerformanceProfile profile, Trend trend) {}
}
