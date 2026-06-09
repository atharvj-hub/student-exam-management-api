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
import java.util.List;
import java.util.Random;

@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class DatabaseSeeder implements CommandLineRunner {

    private final StudentRepository studentRepository;
    private final SubjectRepository subjectRepository;
    private final ExamRepository examRepository;
    private final ResultService resultService;

    @Override
    public void run(String... args) throws Exception {
        if (studentRepository.count() > 0) {
            log.info("Database already seeded. Skipping synthetic data generation.");
            return;
        }

        log.info("Starting synthetic database seeding...");
        Faker faker = new Faker();
        Random random = new Random();

        // 1. Create Subjects
        List<Subject> subjects = createSubjects();
        
        // 2. Create Exams
        List<Exam> exams = createExams(subjects);
        
        // 3. Create Students (50 Section A, 50 Section B)
        List<StudentProfilePair> students = createStudents(faker, random);
        
        // 4. Generate Results
        int resultsCreated = generateResults(students, exams, random);

        log.info("""
            
            =========================================
            Seeding complete
            Students: {}
            Sections: 2
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
        subjects.add(subjectRepository.save(new Subject("Engineering Mechanics", "MECH101", 100)));
        log.info("Subjects created: {}", subjects.size());
        return subjects;
    }

    private List<Exam> createExams(List<Subject> subjects) {
        List<Exam> exams = new ArrayList<>();
        LocalDate baseDate = LocalDate.now().minusMonths(3);

        for (Subject subject : subjects) {
            exams.add(examRepository.save(new Exam("Quiz", subject, baseDate)));
            exams.add(examRepository.save(new Exam("Mid Semester", subject, baseDate.plusMonths(1))));
            exams.add(examRepository.save(new Exam("End Semester", subject, baseDate.plusMonths(2))));
        }
        log.info("Exams created: {}", exams.size());
        return exams;
    }

    private List<StudentProfilePair> createStudents(Faker faker, Random random) {
        List<StudentProfilePair> pairs = new ArrayList<>();

        for (int i = 1; i <= 100; i++) {
            String section = (i <= 50) ? "A" : "B";
            String rollPrefix = String.format("MCA%03d", (i <= 50) ? i : (i - 50));
            String rollNumber = rollPrefix + "-" + section;
            
            String firstName = faker.name().firstName();
            String lastName = faker.name().lastName();
            String name = firstName + " " + lastName;
            String email = firstName.toLowerCase() + "." + lastName.toLowerCase() + rollPrefix.toLowerCase() + "@example.com";

            Student student = new Student(name, email, rollNumber, section);
            student = studentRepository.save(student);

            StudentPerformanceProfile profile = assignProfile(random);
            pairs.add(new StudentProfilePair(student, profile));
        }
        log.info("Students created: {}", pairs.size());
        return pairs;
    }

    private StudentPerformanceProfile assignProfile(Random random) {
        int chance = random.nextInt(100);
        if (chance < 10) return StudentPerformanceProfile.TOP_PERFORMER; // 10%
        if (chance < 35) return StudentPerformanceProfile.ABOVE_AVERAGE; // 25%
        if (chance < 80) return StudentPerformanceProfile.AVERAGE;       // 45%
        if (chance < 95) return StudentPerformanceProfile.WEAK;          // 15%
        return StudentPerformanceProfile.AT_RISK;                        // 5%
    }

    private int generateResults(List<StudentProfilePair> pairs, List<Exam> exams, Random random) {
        int resultsCreated = 0;
        
        for (StudentProfilePair pair : pairs) {
            Student student = pair.student();
            StudentPerformanceProfile profile = pair.profile();
            
            // Determine mean shift based on section (A slightly better)
            double sectionShift = "A".equals(student.getSection()) ? +4.0 : -4.0;
            
            // Base mean and standard deviation based on profile
            double profileMean = getProfileMean(profile);
            double stdDev = getProfileStdDev(profile);
            
            // Final target mean for this student
            double studentMean = profileMean + sectionShift;
            
            // 6 to 8 exams
            int examsToTake = 6 + random.nextInt(3); 
            
            List<Exam> shuffledExams = new ArrayList<>(exams);
            Collections.shuffle(shuffledExams, random);
            List<Exam> selectedExams = shuffledExams.subList(0, Math.min(examsToTake, exams.size()));
            
            for (Exam exam : selectedExams) {
                double totalMarks = exam.getSubject().getTotalMarks();
                
                // Gaussian generation
                double rawMark = studentMean + (random.nextGaussian() * stdDev);
                
                // Scale marks proportionally if totalMarks is not 100
                double scaledMark = (rawMark / 100.0) * totalMarks;
                
                // Clamp marks against subject total marks
                scaledMark = Math.max(0, Math.min(totalMarks, scaledMark));
                
                // Round to nearest 2 decimal places
                double finalMark = Math.round(scaledMark * 100.0) / 100.0;
                
                ResultCreateRequest request = new ResultCreateRequest();
                request.setStudentId(student.getId());
                request.setExamId(exam.getId());
                request.setMarks(finalMark);
                
                resultService.createResult(request);
                resultsCreated++;
            }
        }
        return resultsCreated;
    }

    private double getProfileMean(StudentPerformanceProfile profile) {
        return switch (profile) {
            case TOP_PERFORMER -> 92.0;
            case ABOVE_AVERAGE -> 78.0;
            case AVERAGE -> 63.0;
            case WEAK -> 45.0;
            case AT_RISK -> 25.0;
        };
    }

    private double getProfileStdDev(StudentPerformanceProfile profile) {
        return switch (profile) {
            case TOP_PERFORMER -> 3.0;
            case ABOVE_AVERAGE -> 5.0;
            case AVERAGE -> 6.0;
            case WEAK -> 7.0;
            case AT_RISK -> 10.0;
        };
    }

    private record StudentProfilePair(Student student, StudentPerformanceProfile profile) {}
}
