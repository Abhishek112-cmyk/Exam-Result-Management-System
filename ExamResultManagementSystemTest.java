import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.ArrayList;

/**
 * Automated test runner for ExamResultManagementSystem.
 * Validates all 4 primary test cases from the specification plus edge cases.
 */
public class ExamResultManagementSystemTest {

    private static int testsPassed = 0;
    private static int testsTotal = 0;

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("  RUNNING EXAM RESULT MANAGEMENT SYSTEM TESTS");
        System.out.println("==================================================");

        testCase1_NormalInput();
        testCase2_BoundaryMinimumInput();
        testCase3_InvalidInput();
        testCase4_DifferentValidScenario();
        testNegativeMarksEdgeCase();
        testSearchNotFoundEdgeCase();
        testFilePersistenceRoundTrip();
        testCustomSortingLogicDirectly();
        testCustomSearchingLogicDirectly();
        testNameSanitizationAndExtraMarks();

        System.out.println("\n==================================================");
        System.out.printf("  TEST RESULTS: %d / %d PASSED%n", testsPassed, testsTotal);
        System.out.println("==================================================");

        if (testsPassed != testsTotal) {
            System.exit(1);
        }
    }

    private static void assertTrue(String testName, boolean condition, String details) {
        testsTotal++;
        if (condition) {
            System.out.println("[PASS] " + testName);
            testsPassed++;
        } else {
            System.err.println("[FAIL] " + testName + " - " + details);
        }
    }

    /**
     * Test Case 1: Normal Input
     * Input:
     * Menu option 1 (Add result)
     * Name: Kiran Patel
     * Marks: 80 70 90
     * Expected Output:
     * Result added. ID: ER101 | Kiran Patel | Total: 240/300 | Percentage: 80.00 | Result: Pass
     */
    private static void testCase1_NormalInput() {
        cleanResultsFile();
        ExamResultManagementSystem.setNextIdNumber(101);
        ArrayList<StudentResult> list = new ArrayList<>();

        String simulatedInput = "1\nKiran Patel\n80 70 90\n5\n";
        String output = runWithInput(list, simulatedInput);

        String expected = "Result added. ID: ER101 | Kiran Patel | Total: 240/300 | Percentage: 80.00 | Result: Pass";
        assertTrue("Test Case 1 - Normal Input Output Match",
                output.contains(expected),
                "Expected output to contain: [" + expected + "], but got:\n" + output);

        assertTrue("Test Case 1 - Record Stored in ArrayList",
                list.size() == 1 && list.get(0).getId().equals("ER101") && list.get(0).getTotal() == 240,
                "List record mismatch");
    }

    /**
     * Test Case 2: Boundary / Minimum Input
     * Input:
     * Menu option 1 (Add result)
     * Name: Lalit Soni
     * Marks: 40 40 40
     * Expected Output:
     * Result added. ID: ER101 | Lalit Soni | Total: 120/300 | Percentage: 40.00 | Result: Pass
     */
    private static void testCase2_BoundaryMinimumInput() {
        cleanResultsFile();
        ExamResultManagementSystem.setNextIdNumber(101);
        ArrayList<StudentResult> list = new ArrayList<>();

        String simulatedInput = "1\nLalit Soni\n40 40 40\n5\n";
        String output = runWithInput(list, simulatedInput);

        String expected = "Result added. ID: ER101 | Lalit Soni | Total: 120/300 | Percentage: 40.00 | Result: Pass";
        assertTrue("Test Case 2 - Boundary / Minimum Input Output Match",
                output.contains(expected),
                "Expected output to contain: [" + expected + "], but got:\n" + output);

        assertTrue("Test Case 2 - Percentage is 40.00 and Pass",
                list.size() == 1 && list.get(0).getPercentage() == 40.0 && list.get(0).getResult().equals("Pass"),
                "Boundary condition failed");
    }

    /**
     * Test Case 3: Invalid Input
     * Input:
     * Menu option 1 (Add result)
     * Name: Kiran Patel
     * Marks: 80 105 90
     * Expected Output:
     * Invalid marks. Each mark must be between 0 and 100.
     */
    private static void testCase3_InvalidInput() {
        cleanResultsFile();
        ExamResultManagementSystem.setNextIdNumber(101);
        ArrayList<StudentResult> list = new ArrayList<>();

        String simulatedInput = "1\nKiran Patel\n80 105 90\n5\n";
        String output = runWithInput(list, simulatedInput);

        String expected = "Invalid marks. Each mark must be between 0 and 100.";
        assertTrue("Test Case 3 - Invalid Input Output Match",
                output.contains(expected),
                "Expected output to contain: [" + expected + "], but got:\n" + output);

        assertTrue("Test Case 3 - No Record Added on Invalid Input",
                list.isEmpty(),
                "List should remain empty on invalid input");

        assertTrue("Test Case 3 - ID ER101 Not Consumed",
                ExamResultManagementSystem.getNextIdNumber() == 101,
                "ID counter should not be incremented on invalid input");
    }

    /**
     * Test Case 4: Different Valid Scenario
     * Existing: ER101 | Kiran Patel | 80.00 | Pass
     * (a) Option 1: Mitali Roy, 35 30 38
     *     Expected: Result added. ID: ER102 | Mitali Roy | Total: 103/300 | Percentage: 34.33 | Result: Fail
     * (b) Option 4 (Sort by percentage)
     *     Expected:
     *     1. ER101 | Kiran Patel | 80.00 | Pass
     *     2. ER102 | Mitali Roy | 34.33 | Fail
     * (c) Option 3 (Search): ER102
     *     Expected:
     *     Found: ER102 | Mitali Roy | Total: 103/300 | Percentage: 34.33 | Result: Fail
     */
    private static void testCase4_DifferentValidScenario() {
        cleanResultsFile();
        ArrayList<StudentResult> list = new ArrayList<>();
        // Simulate existing record in file or system: ER101 | Kiran Patel | 80.00 | Pass
        list.add(new StudentResult("ER101", "Kiran Patel", 80, 70, 90));
        ExamResultManagementSystem.updateNextIdNumber(list);

        String simulatedInput = "1\nMitali Roy\n35 30 38\n4\n3\nER102\n5\n";
        String output = runWithInput(list, simulatedInput);

        String expectedA = "Result added. ID: ER102 | Mitali Roy | Total: 103/300 | Percentage: 34.33 | Result: Fail";
        String expectedB1 = "1. ER101 | Kiran Patel | 80.00 | Pass";
        String expectedB2 = "2. ER102 | Mitali Roy | 34.33 | Fail";
        String expectedC = "Found: ER102 | Mitali Roy | Total: 103/300 | Percentage: 34.33 | Result: Fail";

        assertTrue("Test Case 4(a) - Add Mitali Roy Fail Output Match",
                output.contains(expectedA),
                "Missing or incorrect 4(a) output. Expected: " + expectedA);

        assertTrue("Test Case 4(b) - Sort by Percentage (high to low) Output Match",
                output.contains(expectedB1) && output.contains(expectedB2),
                "Missing or incorrect 4(b) output. Expected:\n" + expectedB1 + "\n" + expectedB2);

        assertTrue("Test Case 4(c) - Search by ID (ER102) Output Match",
                output.contains(expectedC),
                "Missing or incorrect 4(c) output. Expected: " + expectedC);
    }

    private static void testNegativeMarksEdgeCase() {
        ArrayList<StudentResult> list = new ArrayList<>();
        String simulatedInput = "1\nTest Student\n-5 50 60\n5\n";
        String output = runWithInput(list, simulatedInput);

        assertTrue("Edge Case - Negative Marks Rejection",
                output.contains("Invalid marks. Each mark must be between 0 and 100."),
                "Should reject negative marks");
    }

    private static void testSearchNotFoundEdgeCase() {
        ArrayList<StudentResult> list = new ArrayList<>();
        list.add(new StudentResult("ER101", "Kiran Patel", 80, 70, 90));
        String simulatedInput = "3\nER999\n5\n";
        String output = runWithInput(list, simulatedInput);

        assertTrue("Edge Case - Search ID Not Found",
                output.contains("Result not found for ID: ER999"),
                "Should display not found message");
    }

    private static void testFilePersistenceRoundTrip() {
        String testFileName = "test_results_roundtrip.txt";
        File testFile = new File(testFileName);
        if (testFile.exists()) testFile.delete();

        ArrayList<StudentResult> original = new ArrayList<>();
        original.add(new StudentResult("ER101", "Kiran Patel", 80, 70, 90));
        original.add(new StudentResult("ER102", "Mitali Roy", 35, 30, 38));

        ExamResultManagementSystem.saveResultsToFile(original, testFileName);

        ArrayList<StudentResult> loaded = new ArrayList<>();
        ExamResultManagementSystem.loadResultsFromFile(loaded, testFileName);

        assertTrue("File Persistence - Both records reloaded",
                loaded.size() == 2,
                "Expected 2 loaded records");

        assertTrue("File Persistence - Record 1 data integrity",
                loaded.get(0).getId().equals("ER101") &&
                        loaded.get(0).getName().equals("Kiran Patel") &&
                        loaded.get(0).getTotal() == 240 &&
                        loaded.get(0).getResult().equals("Pass"),
                "Loaded record 1 mismatch");

        assertTrue("File Persistence - Record 2 data integrity",
                loaded.get(1).getId().equals("ER102") &&
                        loaded.get(1).getName().equals("Mitali Roy") &&
                        loaded.get(1).getTotal() == 103 &&
                        loaded.get(1).getResult().equals("Fail"),
                "Loaded record 2 mismatch");

        testFile.delete();
    }

    private static void testCustomSortingLogicDirectly() {
        ArrayList<StudentResult> list = new ArrayList<>();
        StudentResult r1 = new StudentResult("ER101", "Low Student", 30, 30, 30);      // 30%
        StudentResult r2 = new StudentResult("ER102", "High Student", 90, 90, 90);     // 90%
        StudentResult r3 = new StudentResult("ER103", "Mid Student", 60, 60, 60);      // 60%

        list.add(r1);
        list.add(r2);
        list.add(r3);

        // Sort descending
        ExamResultManagementSystem.sortByPercentage(list);

        assertTrue("Direct Sort Logic - First is Highest",
                list.get(0).getId().equals("ER102") && list.get(0).getPercentage() == 90.0,
                "Sort order failed for index 0");

        assertTrue("Direct Sort Logic - Second is Middle",
                list.get(1).getId().equals("ER103") && list.get(1).getPercentage() == 60.0,
                "Sort order failed for index 1");

        assertTrue("Direct Sort Logic - Third is Lowest",
                list.get(2).getId().equals("ER101") && list.get(2).getPercentage() == 30.0,
                "Sort order failed for index 2");
    }

    private static void testCustomSearchingLogicDirectly() {
        ArrayList<StudentResult> list = new ArrayList<>();
        StudentResult r1 = new StudentResult("ER101", "Kiran Patel", 80, 70, 90);
        StudentResult r2 = new StudentResult("ER102", "Mitali Roy", 35, 30, 38);
        list.add(r1);
        list.add(r2);

        StudentResult foundExact = ExamResultManagementSystem.searchById(list, "ER102");
        assertTrue("Direct Search Logic - Exact Match",
                foundExact != null && foundExact.getName().equals("Mitali Roy"),
                "Search failed for exact match");

        StudentResult foundCaseInsensitive = ExamResultManagementSystem.searchById(list, "er101");
        assertTrue("Direct Search Logic - Case Insensitive Match",
                foundCaseInsensitive != null && foundCaseInsensitive.getName().equals("Kiran Patel"),
                "Search failed for case insensitive match");

        StudentResult notFound = ExamResultManagementSystem.searchById(list, "ER999");
        assertTrue("Direct Search Logic - Non-existent ID returns null",
                notFound == null,
                "Search should return null for non-existent ID");
    }

    private static String runWithInput(ArrayList<StudentResult> list, String inputData) {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));

        try {
            java.util.Scanner testScanner = new java.util.Scanner(new ByteArrayInputStream(inputData.getBytes()));
            while (testScanner.hasNextLine()) {
                ExamResultManagementSystem.printMenu();
                if (!testScanner.hasNextLine()) break;
                String line = testScanner.nextLine().trim();
                if (line.isEmpty()) continue;

                int choice;
                try {
                    choice = Integer.parseInt(line);
                } catch (NumberFormatException e) {
                    continue;
                }

                switch (choice) {
                    case 1:
                        ExamResultManagementSystem.addResult(testScanner, list);
                        break;
                    case 2:
                        ExamResultManagementSystem.displayAll(list);
                        break;
                    case 3:
                        ExamResultManagementSystem.handleSearch(testScanner, list);
                        break;
                    case 4:
                        ExamResultManagementSystem.handleSort(list);
                        break;
                    case 5:
                        return outContent.toString();
                    default:
                        break;
                }
            }
        } finally {
            System.setOut(originalOut);
        }
        return outContent.toString();
    }

    private static void cleanResultsFile() {
        // Intentionally does NOT touch the real results.txt: tests run fully in memory.
    }

    private static void testNameSanitizationAndExtraMarks() {
        ExamResultManagementSystem.setNextIdNumber(101);
        ArrayList<StudentResult> list = new ArrayList<>();
        runWithInput(list, "1\nPatel, Kiran\n80 70 90\n5\n");
        assertTrue("Edge Case - Comma in name is sanitized",
                list.size() == 1 && list.get(0).getName().equals("Patel Kiran"),
                "Name was not sanitized");
        StudentResult reparsed = ExamResultManagementSystem.parseLine(list.get(0).toFileString());
        assertTrue("Edge Case - Sanitized record survives file round trip",
                reparsed != null && reparsed.getName().equals("Patel Kiran"),
                "Record lost on reload");

        ArrayList<StudentResult> list2 = new ArrayList<>();
        String out = runWithInput(list2, "1\nA B\n80 70 90 999\n5\n");
        assertTrue("Edge Case - More than 3 marks rejected",
                list2.isEmpty() && out.contains("exactly 3 marks"),
                "Extra marks were accepted");
    }
}
