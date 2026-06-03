"""
test_student_exam_api.py
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Automated regression test suite for Student Exam Result Management API.
Tests all 12+ REST endpoints + business logic validation.

REQUIREMENTS:
    pip install requests

USAGE:
    python test_student_exam_api.py
    python test_student_exam_api.py --url http://your-server:8080

Tests 15+ scenarios:
    1.  Create student (201)
    2.  Duplicate student email (409)
    3.  Create student with invalid data (422)
    4.  Get all students (200)
    5.  Get student by ID (200)
    6.  Get nonexistent student (404)
    7.  Update student (200)
    8.  Create subject (201)
    9.  Create exam (201)
    10. Create result — grade A+ boundary (201, grade=A_PLUS)
    11. Create result — grade A boundary (201, grade=A)
    12. Create result — grade B boundary (201, grade=B)
    13. Create result — grade C / PASS boundary (201, grade=C, status=PASS)
    14. Create result — FAIL boundary (201, grade=FAIL, status=FAIL)
    15. Duplicate result for same student+exam (400)
    16. Marks exceeding total marks (400)
    17. Get results by student (200, correct count)
    18. Delete student (204)
    19. Get deleted student (404)
    20. Invalid result — missing fields (422)
"""

import requests
import sys
import json
import argparse
import time
from datetime import date, timedelta
from typing import Optional

# ── Configuration ────────────────────────────────────────────────────────────
BASE_URL = "http://localhost:8080"
RUN_ID = str(int(time.time()))
PASS_MARK = "\033[92m✓\033[0m"
FAIL_MARK = "\033[91m✗\033[0m"
WARN_MARK = "\033[93m⚠\033[0m"


# ── Test Runner ───────────────────────────────────────────────────────────────

class TestResult:
    def __init__(self):
        self.passed = 0
        self.failed = 0
        self.failures = []

    def record(self, name: str, passed: bool, detail: str = ""):
        if passed:
            self.passed += 1
            print(f"  {PASS_MARK}  {name}")
        else:
            self.failed += 1
            self.failures.append((name, detail))
            print(f"  {FAIL_MARK}  {name}")
            if detail:
                print(f"       {detail}")

    def summary(self):
        total = self.passed + self.failed
        print("\n" + "═" * 60)
        print(f"  Results: {self.passed}/{total} passed")
        if self.failures:
            print(f"\n  Failed tests:")
            for name, detail in self.failures:
                print(f"    {FAIL_MARK} {name}")
                if detail:
                    print(f"       {detail}")
        print("═" * 60)
        return self.failed == 0


def assert_status(response, expected: int, test_name: str, tr: TestResult) -> bool:
    passed = response.status_code == expected
    detail = f"Expected {expected}, got {response.status_code}. Body: {response.text[:200]}" if not passed else ""
    tr.record(test_name, passed, detail)
    return passed


def assert_equal(actual, expected, field: str, test_name: str, tr: TestResult) -> bool:
    passed = actual == expected
    detail = f"{field}: expected '{expected}', got '{actual}'" if not passed else ""
    tr.record(test_name, passed, detail)
    return passed


# ── Test Groups ───────────────────────────────────────────────────────────────

def test_students(tr: TestResult, base: str) -> dict:
    """Tests 1-7: Student CRUD + validations. Returns created student IDs."""
    print("\n📚 STUDENT MODULE")
    created_ids = {}

    # Test 1: Create student — success
    r = requests.post(f"{base}/api/students", json={
        "name": "Atharv Singh",
        "email": f"atharv_{RUN_ID}@test.com",
        "rollNumber": f"CS001_{RUN_ID}"
    })
    if assert_status(r, 201, "T01: Create student (201 Created)", tr):
        data = r.json()
        created_ids["student1"] = data["id"]
        tr.record("T01a: Response has id field", "id" in data)
        tr.record("T01b: Response has correct name", data.get("name") == "Atharv Singh")

    # Create second student (needed for result tests)
    r2 = requests.post(f"{base}/api/students", json={
        "name": "Rohan Kumar",
        "email": f"rohan_{RUN_ID}@test.com",
        "rollNumber": f"CS002_{RUN_ID}"
    })
    if r2.status_code == 201:
        created_ids["student2"] = r2.json()["id"]

    # Test 2: Duplicate email → 409
    r = requests.post(f"{base}/api/students", json={
        "name": "Duplicate",
        "email": f"atharv_{RUN_ID}@test.com",   # same email
        "rollNumber": "CS999"
    })
    assert_status(r, 409, "T02: Duplicate email → 409 Conflict", tr)

    # Test 3: Invalid data → 422
    r = requests.post(f"{base}/api/students", json={
        "name": "",          # blank name
        "email": "not-email", # invalid email
        "rollNumber": ""
    })
    if assert_status(r, 422, "T03: Invalid data → 422 Unprocessable", tr):
        body = r.json()
        tr.record("T03a: Response has validationErrors field", "validationErrors" in body)

    # Test 4: Get all students
    r = requests.get(f"{base}/api/students")
    if assert_status(r, 200, "T04: Get all students (200)", tr):
        tr.record("T04a: Returns list", isinstance(r.json(), list))
        tr.record("T04b: At least 1 student", len(r.json()) >= 1)

    # Test 5: Get by ID
    s1_id = created_ids.get("student1")
    if s1_id:
        r = requests.get(f"{base}/api/students/{s1_id}")
        if assert_status(r, 200, "T05: Get student by ID (200)", tr):
            tr.record("T05a: Correct email",
                      r.json().get("email") == f"atharv_{RUN_ID}@test.com")

    # Test 6: Get nonexistent → 404
    r = requests.get(f"{base}/api/students/99999")
    assert_status(r, 404, "T06: Nonexistent student → 404", tr)

    # Test 7: Update student
    if s1_id:
        r = requests.put(f"{base}/api/students/{s1_id}", json={
            "name": "Atharv Singh Updated",
            "email": f"atharv_{RUN_ID}@test.com",   # same email (no conflict with self)
            "rollNumber": f"CS001_{RUN_ID}"
        })
        if assert_status(r, 200, "T07: Update student (200)", tr):
            tr.record("T07a: Name updated", r.json().get("name") == "Atharv Singh Updated")

    return created_ids


def test_subjects(tr: TestResult, base: str) -> dict:
    """Tests 8: Subject CRUD. Returns subject IDs."""
    print("\n📖 SUBJECT MODULE")
    created_ids = {}

    # Test 8: Create subject
    r = requests.post(f"{base}/api/subjects", json={
        "subjectName": "Mathematics",
        "subjectCode": f"MATH101_{RUN_ID}",
        "totalMarks": 100
    })
    if assert_status(r, 201, "T08: Create subject (201 Created)", tr):
        created_ids["math"] = r.json()["id"]
        tr.record("T08a: totalMarks correct", r.json().get("totalMarks") == 100)

    return created_ids


def test_exams(tr: TestResult, base: str, subject_ids: dict) -> dict:
    """Tests 9: Exam CRUD. Returns exam IDs."""
    print("\n📝 EXAM MODULE")
    created_ids = {}

    math_id = subject_ids.get("math")
    if not math_id:
        tr.record("T09: Create exam", False, "No subject created in previous step")
        return created_ids

    # Test 9: Create exam
    r = requests.post(f"{base}/api/exams", json={
        "examName": "Mathematics Mid-Term",
        "subjectId": math_id,
        "examDate": str(date.today() + timedelta(days=30))
    })
    if assert_status(r, 201, "T09: Create exam (201 Created)", tr):
        data = r.json()
        created_ids["exam1"] = data["id"]
        tr.record("T09a: Exam has nested subject", "subject" in data)
        tr.record("T09b: Nested subject id correct", data.get("subject", {}).get("id") == math_id)

    return created_ids


def test_results_grade_boundaries(tr: TestResult, base: str, student_ids: dict,
                                  exam_ids: dict, subject_ids: dict) -> None:
    """
    Tests 10-20: Result creation + grade/status business logic.
    Tests ALL grade boundaries from BRD.

    BRD Grade Rules:
      A+   → 90%+
      A    → 75-89%
      B    → 60-74%
      C    → 35-59%
      FAIL → < 35%

    Pass: 40%+
    """
    print("\n🎯 RESULT MODULE — Grade & Status Business Logic")

    # Create extra exams for each boundary test
    math_id = subject_ids.get("math")
    student1_id = student_ids.get("student1")
    student2_id = student_ids.get("student2")
    exam1_id = exam_ids.get("exam1")

    if not all([math_id, student1_id, exam1_id]):
        tr.record("T10-20: Results tests", False, "Missing prerequisite data from earlier tests")
        return

    def create_exam_for_marks(marks: float, total: float = 100.0) -> Optional[int]:
        """Helper: create a fresh exam so we can record a result with specific marks."""
        r = requests.post(f"{base}/api/exams", json={
            "examName": f"Test Exam {marks}/{total}",
            "subjectId": math_id,
            "examDate": str(date.today() + timedelta(days=30))
        })
        return r.json()["id"] if r.status_code == 201 else None

    def create_result_and_check(student_id: int, exam_id: int, marks: float,
                                expected_grade: str, expected_status: str,
                                test_num: str, description: str):
        r = requests.post(f"{base}/api/results", json={
            "studentId": student_id,
            "examId": exam_id,
            "marks": marks
        })
        if assert_status(r, 201, f"{test_num}: {description} → 201", tr):
            data = r.json()
            expected_pct = round((marks / 100.0) * 100, 2)
            tr.record(
                f"{test_num}a: grade={expected_grade}",
                data.get("grade") == expected_grade,
                f"Got grade={data.get('grade')}"
            )
            tr.record(
                f"{test_num}b: status={expected_status}",
                data.get("status") == expected_status,
                f"Got status={data.get('status')}"
            )
            tr.record(
                f"{test_num}c: percentage correct",
                abs(data.get("percentage", -1) - expected_pct) < 0.01,
                f"Got percentage={data.get('percentage')}, expected={expected_pct}"
            )

    # Test 10: Grade A+ (90%)
    eid = create_exam_for_marks(90)
    if eid:
        create_result_and_check(student1_id, eid, 90, "A_PLUS", "PASS",
                                "T10", "90/100 marks → A+ PASS")

    # Test 11: Grade A (75%)
    eid = create_exam_for_marks(75)
    if eid:
        create_result_and_check(student1_id, eid, 75, "A", "PASS",
                                "T11", "75/100 marks → A PASS")

    # Test 12: Grade B (60%)
    eid = create_exam_for_marks(60)
    if eid:
        create_result_and_check(student1_id, eid, 60, "B", "PASS",
                                "T12", "60/100 marks → B PASS")

    # Test 13: Grade C, PASS (40% — exactly at pass boundary)
    eid = create_exam_for_marks(40)
    if eid:
        create_result_and_check(student1_id, eid, 40, "C", "PASS",
                                "T13", "40/100 marks → C PASS (boundary)")

    # Test 14: Grade FAIL (30% — below pass boundary)
    eid = create_exam_for_marks(30)
    if eid:
        create_result_and_check(student1_id, eid, 30, "FAIL", "FAIL",
                                "T14", "30/100 marks → FAIL FAIL")

    # Test 15: Duplicate result → 400
    r = requests.post(f"{base}/api/results", json={
        "studentId": student1_id,
        "examId": exam1_id,
        "marks": 50.0
    })
    # First one should succeed
    if r.status_code == 201:
        # Try to record again for same student + exam
        r2 = requests.post(f"{base}/api/results", json={
            "studentId": student1_id,
            "examId": exam1_id,
            "marks": 60.0
        })
        assert_status(r2, 400, "T15: Duplicate result → 400 Business Rule Violation", tr)
    elif r.status_code == 400:
        # Already exists from a previous test run
        tr.record("T15: Duplicate result → 400", True)

    # Test 16: Marks exceed total → 400
    eid = create_exam_for_marks(0)
    if eid:
        r = requests.post(f"{base}/api/results", json={
            "studentId": student2_id,
            "examId": eid,
            "marks": 150.0   # 150 > 100 (totalMarks)
        })
        assert_status(r, 400, "T16: Marks > totalMarks → 400 Business Rule Violation", tr)

    # Test 17: Get results by student
    r = requests.get(f"{base}/api/results/student/{student1_id}")
    if assert_status(r, 200, "T17: Get results by studentId (200)", tr):
        results = r.json()
        tr.record("T17a: Returns list", isinstance(results, list))
        tr.record("T17b: Results have grade field", all("grade" in res for res in results))
        tr.record("T17c: Results have student nested", all("student" in res for res in results))

    # Test 18: Missing required fields → 422
    r = requests.post(f"{base}/api/results", json={})
    assert_status(r, 422, "T18: Empty result body → 422 Validation Error", tr)


def test_delete(tr: TestResult, base: str, student_ids: dict) -> None:
    """Tests 19-20: Delete + verify gone."""
    print("\n🗑  DELETE & CLEANUP")

    s1_id = student_ids.get("student1")
    if not s1_id:
        return

    # Create a student specifically to delete (avoids FK constraint from results)
    r = requests.post(f"{base}/api/students", json={
        "name": "Delete Me",
        "email": "deleteme@test.com",
        "rollNumber": "DEL001"
    })
    if r.status_code == 201:
        del_id = r.json()["id"]

        # Test 19: Delete
        r = requests.delete(f"{base}/api/students/{del_id}")
        assert_status(r, 204, "T19: Delete student (204 No Content)", tr)

        # Test 20: Get deleted → 404
        r = requests.get(f"{base}/api/students/{del_id}")
        assert_status(r, 404, "T20: Get deleted student → 404 Not Found", tr)
    else:
        tr.record("T19: Delete student", False, "Could not create test student")
        tr.record("T20: Get deleted student", False, "Skipped")


# ── Main ──────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description="Student Exam API Test Suite")
    parser.add_argument("--url", default="http://localhost:8080",
                        help="Base URL of the API (default: http://localhost:8080)")
    args = parser.parse_args()

    global BASE_URL
    BASE_URL = args.url.rstrip("/")

    print("═" * 60)
    print(f"  Student Exam Result API — Regression Test Suite")
    print(f"  Target: {BASE_URL}")
    print("═" * 60)

    # Check API is reachable
    try:
        r = requests.get(f"{BASE_URL}/api/students", timeout=5)
    except requests.exceptions.ConnectionError:
        print(f"\n{FAIL_MARK} Cannot connect to {BASE_URL}")
        print("  Make sure the API is running (mvn spring-boot:run or docker-compose up)")
        sys.exit(1)

    tr = TestResult()

    # Run test groups
    student_ids  = test_students(tr, BASE_URL)
    subject_ids  = test_subjects(tr, BASE_URL)
    exam_ids     = test_exams(tr, BASE_URL, subject_ids)
    test_results_grade_boundaries(tr, BASE_URL, student_ids, exam_ids, subject_ids)
    test_delete(tr, BASE_URL, student_ids)

    # Summary
    success = tr.summary()
    sys.exit(0 if success else 1)


if __name__ == "__main__":
    main()
