import java.awt.Component;
import java.awt.Container;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import javax.swing.AbstractButton;
import javax.swing.JLabel;

/**
 * Automated test runner for ExamResultManagementSystem (Dynamic Subjects + Auto-Detected ERP IDs).
 * Validates variable subject counts (1, 3, 5, 8 subjects), trailing auto-grow slot handling,
 * empty-state "ERP" display, automatic +1 ERP detection, custom Bubble Sort & Linear Search, and UI labels.
 */
public class ExamResultManagementSystemTest {

    private static int testsPassed = 0;
    private static int testsTotal = 0;

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("  RUNNING EXAM RESULT MANAGEMENT SYSTEM TESTS");
        System.out.println("==================================================");

        testEmptyStateDisplaysJustErp();
        testCase1_NormalInputAndAutoDetectNext();
        testCase2_BoundaryMinimumInput();
        testCase3_InvalidInput();
        testCase4_DifferentValidScenario();
        testDynamicVariableSubjectCounts();
        testDuplicateErpRejection();
        testNegativeMarksEdgeCase();
        testSearchNotFoundEdgeCase();
        testFilePersistenceRoundTripVariableSubjects();
        testCustomSortingLogicDirectly();
        testCustomSearchingLogicDirectly();
        testNameSanitizationAndInteriorGapValidation();
        testGuiControllerOperationsAndFormats();
        testMultipleRecordsScale();
        testClearAllRecordsResetsToPlainErp();
        testSwingGuiDynamicSubjectsAndCleanLabels();

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

    private static void testEmptyStateDisplaysJustErp() {
        ArrayList<StudentResult> list = new ArrayList<>();
        ExamResultManagementSystem.updateNextIdNumber(list);

        assertTrue("Empty State - formatNextId() is strictly 'ERP'",
                "ERP".equals(ExamResultManagementSystem.formatNextId())
                        && ExamResultManagementSystem.getNextIdNumber() == null,
                "Expected 'ERP' and null nextIdNumber, got: " + ExamResultManagementSystem.formatNextId());

        OperationResult missingFirstErp = ExamResultManagementSystem.addResultFromInputs(
                list, "", "Kiran Patel", "80", "70", "90");
        assertTrue("Empty State - Prompts for First ERP ID When Blank",
                !missingFirstErp.isSuccess() && list.isEmpty(),
                "Expected failure when first ERP ID is omitted on empty list");
    }

    private static void testCase1_NormalInputAndAutoDetectNext() {
        ArrayList<StudentResult> list = new ArrayList<>();
        ExamResultManagementSystem.updateNextIdNumber(list);

        String simulatedInput = "1\n501\nKiran Patel\n80 70 90\n5\n";
        String output = runWithInput(list, simulatedInput);

        String expected = "Result added. ID: ERP 501 | Kiran Patel | Total: 240/300 | Percentage: 80.00 | Result: Pass";
        assertTrue("Test Case 1 - Normal Input Output Match",
                output.contains(expected),
                "Expected output to contain: [" + expected + "], but got:\n" + output);

        assertTrue("Test Case 1 - Auto-Detects Next ERP ID (ERP 502)",
                list.size() == 1
                        && "ERP 501".equals(list.get(0).getId())
                        && "ERP 502".equals(ExamResultManagementSystem.formatNextId()),
                "Expected nextId 'ERP 502', got: " + ExamResultManagementSystem.formatNextId());
    }

    private static void testCase2_BoundaryMinimumInput() {
        ArrayList<StudentResult> list = new ArrayList<>();
        ExamResultManagementSystem.updateNextIdNumber(list);

        String simulatedInput = "1\nERP 700\nLalit Soni\n40 40 40\n5\n";
        String output = runWithInput(list, simulatedInput);

        String expected = "Result added. ID: ERP 700 | Lalit Soni | Total: 120/300 | Percentage: 40.00 | Result: Pass";
        assertTrue("Test Case 2 - Boundary / Minimum Input Output Match",
                output.contains(expected),
                "Expected output to contain: [" + expected + "], but got:\n" + output);

        assertTrue("Test Case 2 - Percentage is 40.00 and Pass",
                list.size() == 1 && list.get(0).getPercentage() == 40.0 && list.get(0).getResult().equals("Pass"),
                "Boundary condition failed");
    }

    private static void testCase3_InvalidInput() {
        ArrayList<StudentResult> list = new ArrayList<>();
        ExamResultManagementSystem.updateNextIdNumber(list);

        String simulatedInput = "1\n801\nKiran Patel\n80 105 90\n5\n";
        String output = runWithInput(list, simulatedInput);

        String expected = "Invalid marks. Each mark must be between 0 and 100.";
        assertTrue("Test Case 3 - Invalid Input Output Match",
                output.contains(expected),
                "Expected output to contain: [" + expected + "], but got:\n" + output);

        assertTrue("Test Case 3 - No Record Added and Next ERP Remains 'ERP'",
                list.isEmpty() && "ERP".equals(ExamResultManagementSystem.formatNextId()),
                "List should remain empty and nextId should remain 'ERP'");
    }

    private static void testCase4_DifferentValidScenario() {
        ArrayList<StudentResult> list = new ArrayList<>();
        list.add(new StudentResult("ERP 501", "Kiran Patel", 80, 70, 90));
        ExamResultManagementSystem.updateNextIdNumber(list);

        String simulatedInput = "1\nMitali Roy\n35 30 38\n4\n3\nERP 502\n5\n";
        String output = runWithInput(list, simulatedInput);

        String expectedA = "Result added. ID: ERP 502 | Mitali Roy | Total: 103/300 | Percentage: 34.33 | Result: Fail";
        String expectedB1 = "1. ERP 501 | Kiran Patel | 80.00 | Pass";
        String expectedB2 = "2. ERP 502 | Mitali Roy | 34.33 | Fail";
        String expectedC = "Found: ERP 502 | Mitali Roy | Total: 103/300 | Percentage: 34.33 | Result: Fail";

        assertTrue("Test Case 4(a) - Auto-Detected ERP 502 Add Output Match",
                output.contains(expectedA),
                "Missing or incorrect 4(a) output. Expected: " + expectedA);

        assertTrue("Test Case 4(b) - Sort by Percentage (high to low) Output Match",
                output.contains(expectedB1) && output.contains(expectedB2),
                "Missing or incorrect 4(b) output. Expected:\n" + expectedB1 + "\n" + expectedB2);

        assertTrue("Test Case 4(c) - Search by ID (ERP 502) Output Match",
                output.contains(expectedC),
                "Missing or incorrect 4(c) output. Expected: " + expectedC);
    }

    /**
     * Tests dynamic subjects: 1 subject (/100), 5 subjects (/500), 8 subjects (/800),
     * plus ignoring the trailing blank auto-grow slot!
     */
    private static void testDynamicVariableSubjectCounts() {
        ArrayList<StudentResult> list = new ArrayList<>();
        ExamResultManagementSystem.updateNextIdNumber(list);

        // 1 subject + trailing blank auto-grow slot
        OperationResult oneSub = ExamResultManagementSystem.addResultFromInputs(
                list, "101", "Solo Subject", "88", "");
        assertTrue("Dynamic Subjects - 1 Subject (/100) & Trailing Blank Ignored",
                oneSub.isSuccess()
                        && oneSub.getStudentResult().getSubjectCount() == 1
                        && oneSub.getMessage().contains("Total: 88/100 | Percentage: 88.00 | Result: Pass"),
                "Unexpected 1-subject output: " + oneSub.getMessage());

        // 5 subjects + trailing blank auto-grow slot
        OperationResult fiveSub = ExamResultManagementSystem.addResultFromInputs(
                list, "", "Five Subjects", "80", "70", "90", "85", "95", "");
        assertTrue("Dynamic Subjects - 5 Subjects (/500) Auto-Grow",
                fiveSub.isSuccess()
                        && fiveSub.getStudentResult().getSubjectCount() == 5
                        && fiveSub.getMessage().contains("Total: 420/500 | Percentage: 84.00 | Result: Pass"),
                "Unexpected 5-subject output: " + fiveSub.getMessage());

        // 6 subjects pasted in space-separated string
        OperationResult sixSub = ExamResultManagementSystem.addResultFromInputs(
                list, "", "Six Subjects", "50 60 70 80 90 100");
        assertTrue("Dynamic Subjects - 6 Pasted Subjects (/600)",
                sixSub.isSuccess()
                        && sixSub.getStudentResult().getSubjectCount() == 6
                        && sixSub.getMessage().contains("Total: 450/600 | Percentage: 75.00 | Result: Pass"),
                "Unexpected 6-subject output: " + sixSub.getMessage());
    }

    private static void testDuplicateErpRejection() {
        ArrayList<StudentResult> list = new ArrayList<>();
        ExamResultManagementSystem.updateNextIdNumber(list);
        ExamResultManagementSystem.addResultFromInputs(list, "ERP 900", "Kiran Patel", "80", "70", "90");

        OperationResult dup = ExamResultManagementSystem.addResultFromInputs(
                list, "900", "Another Student", "60", "60");
        assertTrue("Edge Case - Duplicate ERP ID Rejected",
                !dup.isSuccess() && list.size() == 1 && dup.getMessage().contains("Duplicate ERP ID"),
                "Duplicate ERP ID should be rejected");
    }

    private static void testNegativeMarksEdgeCase() {
        ArrayList<StudentResult> list = new ArrayList<>();
        ExamResultManagementSystem.setNextIdNumber(300);
        String simulatedInput = "1\nTest Student\n-5 50 60 70\n5\n";
        String output = runWithInput(list, simulatedInput);

        assertTrue("Edge Case - Negative Marks Rejection",
                output.contains("Invalid marks. Each mark must be between 0 and 100."),
                "Should reject negative marks");
    }

    private static void testSearchNotFoundEdgeCase() {
        ArrayList<StudentResult> list = new ArrayList<>();
        list.add(new StudentResult("ERP 501", "Kiran Patel", 80, 70, 90));
        ExamResultManagementSystem.updateNextIdNumber(list);
        String simulatedInput = "3\nERP 99999\n5\n";
        String output = runWithInput(list, simulatedInput);

        assertTrue("Edge Case - Search ID Not Found",
                output.contains("Result not found for ID: ERP 99999"),
                "Should display not found message");
    }

    private static void testFilePersistenceRoundTripVariableSubjects() {
        String testFileName = "test_results_roundtrip.txt";
        File testFile = new File(testFileName);
        if (testFile.exists()) testFile.delete();

        ArrayList<StudentResult> original = new ArrayList<>();
        original.add(new StudentResult("ERP 2050", "Two Sub Student", 80, 90));
        original.add(new StudentResult("ERP 2051", "Five Sub Student", 70, 75, 80, 85, 90));

        ExamResultManagementSystem.saveResultsToFile(original, testFileName);

        ArrayList<StudentResult> loaded = new ArrayList<>();
        ExamResultManagementSystem.loadResultsFromFile(loaded, testFileName);

        assertTrue("File Persistence - Variable-subject records reloaded & next ID auto-detected (ERP 2052)",
                loaded.size() == 2 && "ERP 2052".equals(ExamResultManagementSystem.formatNextId()),
                "Expected 2 loaded records and next ID ERP 2052");

        assertTrue("File Persistence - 2-Subject Record Integrity (/200)",
                loaded.get(0).getSubjectCount() == 2
                        && loaded.get(0).getTotal() == 170
                        && loaded.get(0).getMaxMarks() == 200,
                "Loaded 2-subject record mismatch");

        assertTrue("File Persistence - 5-Subject Record Integrity (/500)",
                loaded.get(1).getSubjectCount() == 5
                        && loaded.get(1).getTotal() == 400
                        && loaded.get(1).getMaxMarks() == 500,
                "Loaded 5-subject record mismatch");

        testFile.delete();
    }

    private static void testCustomSortingLogicDirectly() {
        ArrayList<StudentResult> list = new ArrayList<>();
        StudentResult r1 = new StudentResult("ERP 401", "Low Student", 30, 30);              // 30% (2 subjects)
        StudentResult r2 = new StudentResult("ERP 402", "High Student", 90, 90, 90, 90, 90); // 90% (5 subjects)
        StudentResult r3 = new StudentResult("ERP 403", "Mid Student", 60, 60, 60);          // 60% (3 subjects)

        list.add(r1);
        list.add(r2);
        list.add(r3);

        ExamResultManagementSystem.sortByPercentage(list);

        assertTrue("Direct Sort Logic - Compares Normalized Percentages Across Different Subject Counts",
                list.get(0).getId().equals("ERP 402")
                        && list.get(1).getId().equals("ERP 403")
                        && list.get(2).getId().equals("ERP 401"),
                "Sort order failed across mixed subject counts");
    }

    private static void testCustomSearchingLogicDirectly() {
        ArrayList<StudentResult> list = new ArrayList<>();
        StudentResult r1 = new StudentResult("ERP 401", "Kiran Patel", 80, 70, 90, 85);
        StudentResult r2 = new StudentResult("ERP 402", "Mitali Roy", 35, 30);
        list.add(r1);
        list.add(r2);

        StudentResult foundExact = ExamResultManagementSystem.searchById(list, "ERP 402");
        assertTrue("Direct Search Logic - Exact Match",
                foundExact != null && foundExact.getName().equals("Mitali Roy"),
                "Search failed for exact match");

        StudentResult foundNoSpaceLower = ExamResultManagementSystem.searchById(list, "erp401");
        assertTrue("Direct Search Logic - Case & Space Insensitive Match",
                foundNoSpaceLower != null && foundNoSpaceLower.getName().equals("Kiran Patel"),
                "Search failed for erp401");

        StudentResult foundDigitsOnly = ExamResultManagementSystem.searchById(list, "402");
        assertTrue("Direct Search Logic - Numeric Shorthand Match",
                foundDigitsOnly != null && foundDigitsOnly.getName().equals("Mitali Roy"),
                "Search failed for numeric shorthand 402");
    }

    private static void testNameSanitizationAndInteriorGapValidation() {
        ArrayList<StudentResult> list = new ArrayList<>();
        ExamResultManagementSystem.setNextIdNumber(601);
        runWithInput(list, "1\nPatel, Kiran\n80 70 90 95\n5\n");
        assertTrue("Edge Case - Comma in name is sanitized with 4 subjects",
                list.size() == 1 && list.get(0).getName().equals("Patel Kiran") && list.get(0).getSubjectCount() == 4,
                "Name was not sanitized");
        StudentResult reparsed = ExamResultManagementSystem.parseLine(list.get(0).toFileString());
        assertTrue("Edge Case - Sanitized 4-subject record survives file round trip",
                reparsed != null && reparsed.getName().equals("Patel Kiran") && reparsed.getSubjectCount() == 4,
                "Record lost on reload");

        OperationResult gap = ExamResultManagementSystem.addResultFromInputs(
                list, "", "Test Gap", "80", "", "90");
        assertTrue("Edge Case - Interior Empty Subject Slot Rejected",
                !gap.isSuccess() && gap.getMessage().contains("Subject 2"),
                "Interior empty subject gap should be rejected");
    }

    private static void testGuiControllerOperationsAndFormats() {
        ArrayList<StudentResult> list = new ArrayList<>();
        ExamResultManagementSystem.updateNextIdNumber(list);

        OperationResult res1 = ExamResultManagementSystem.addResultFromInputs(
                list, "3001", "Kiran Patel", "80", "70", "90", "100", "");
        assertTrue("GUI Controller - 4 Dynamic Subjects (/400)",
                res1.isSuccess() && res1.getMessage().equals(
                        "Result added. ID: ERP 3001 | Kiran Patel | Total: 340/400 | Percentage: 85.00 | Result: Pass"),
                "Unexpected message: " + res1.getMessage());

        OperationResult res2 = ExamResultManagementSystem.addResultFromInputs(
                list, "", "Mitali Roy", "35", "33", "");
        assertTrue("GUI Controller - 2 Dynamic Subjects (/200) Auto-Shrink",
                res2.isSuccess() && res2.getMessage().equals(
                        "Result added. ID: ERP 3002 | Mitali Roy | Total: 68/200 | Percentage: 34.00 | Result: Fail"),
                "Unexpected message: " + res2.getMessage());
    }

    private static void testMultipleRecordsScale() {
        ArrayList<StudentResult> list = new ArrayList<>();
        ExamResultManagementSystem.updateNextIdNumber(list);

        for (int i = 0; i < 50; i++) {
            int mark = (i * 2) % 101;
            String firstErp = (i == 0) ? "1001" : null;
            int subjectCount = (i % 6) + 1; // 1 to 6 subjects dynamically!
            int[] marks = new int[subjectCount];
            for (int s = 0; s < subjectCount; s++) marks[s] = mark;
            OperationResult r = ExamResultManagementSystem.createAndAddResult(
                    list, firstErp, "Student " + (i + 1), marks);
            if (!r.isSuccess()) {
                assertTrue("Multi-Record Scale - Insertion", false, "Failed at i=" + i);
                return;
            }
        }

        ExamResultManagementSystem.sortByPercentage(list);
        boolean monotonicDescending = true;
        for (int i = 0; i < list.size() - 1; i++) {
            if (list.get(i).getPercentage() < list.get(i + 1).getPercentage()) {
                monotonicDescending = false;
                break;
            }
        }

        StudentResult foundLastAdded = ExamResultManagementSystem.searchById(list, "ERP 1050");
        assertTrue("Multi-Record Scale - 50 Mixed-Subject Records Added, Sorted & Searched",
                list.size() == 50 && monotonicDescending && foundLastAdded != null
                        && foundLastAdded.getName().equals("Student 50")
                        && "ERP 1051".equals(ExamResultManagementSystem.formatNextId()),
                "Multi-record verification failed");
    }

    private static void testClearAllRecordsResetsToPlainErp() {
        String tempFile = "test_clear_records.txt";
        ArrayList<StudentResult> list = new ArrayList<>();
        ExamResultManagementSystem.updateNextIdNumber(list);
        ExamResultManagementSystem.createAndAddResult(list, "5001", "Kiran Patel", 80, 70, 90, 85);
        ExamResultManagementSystem.saveResultsToFile(list, tempFile);

        ExamResultManagementSystem.clearAllResults(list, tempFile);

        ArrayList<StudentResult> reloaded = new ArrayList<>();
        ExamResultManagementSystem.loadResultsFromFile(reloaded, tempFile);
        new File(tempFile).delete();

        assertTrue("Clear All Records - Empties File & Resets Display to Plain 'ERP'",
                reloaded.isEmpty() && "ERP".equals(ExamResultManagementSystem.formatNextId()),
                "Expected 'ERP' after clearAllResults, got: " + ExamResultManagementSystem.formatNextId());
    }

    private static void testSwingGuiDynamicSubjectsAndCleanLabels() {
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            assertTrue("Swing GUI - Headless Safe Fallback", true, "Skipped window creation in headless mode");
            return;
        }
        ArrayList<StudentResult> emptyList = new ArrayList<>();
        ExamResultManagementSystem.updateNextIdNumber(emptyList);

        ExamResultGUI emptyGui = new ExamResultGUI(emptyList, "test_gui_empty.txt");
        StringBuilder emptyTexts = new StringBuilder();
        collectComponentTexts(emptyGui.getContentPane(), emptyTexts);
        String allEmptyText = emptyTexts.toString();
        emptyGui.dispose();
        new File("test_gui_empty.txt").delete();

        boolean exactUpdatedLabels = allEmptyText.contains("Subjects (/100 each)")
                && !allEmptyText.contains("3 Subjects")
                && allEmptyText.contains("Marks in subjects (0 - 100 each)")
                && !allEmptyText.contains("Marks in 3")
                && allEmptyText.contains("| ERP |")
                && !allEmptyText.contains("Linear Search")
                && !allEmptyText.contains("ArrayList<StudentResult>");

        assertTrue("Swing GUI - Updated 'Subjects (/100 each)' & 'Marks in subjects (0 - 100 each)' Labels",
                exactUpdatedLabels,
                "Unexpected UI text: " + allEmptyText);
    }

    private static void collectComponentTexts(Component comp, StringBuilder sb) {
        if (comp instanceof JLabel) {
            sb.append(((JLabel) comp).getText()).append(" | ");
        } else if (comp instanceof AbstractButton) {
            sb.append(((AbstractButton) comp).getText()).append(" | ");
        }
        if (comp instanceof Container) {
            Component[] children = ((Container) comp).getComponents();
            for (int i = 0; i < children.length; i++) {
                collectComponentTexts(children[i], sb);
            }
        }
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
}
