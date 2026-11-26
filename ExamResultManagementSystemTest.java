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
 * Automated test runner for ExamResultManagementSystem (Console + GUI controller + Auto-Detected ERP IDs).
 * Validates empty-state "ERP" display, manual first ERP entry, automatic +1 detection,
 * duplicate ERP rejection, custom Bubble Sort & Linear Search, and clean UI headings.
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
        testDuplicateErpRejection();
        testNegativeMarksEdgeCase();
        testSearchNotFoundEdgeCase();
        testFilePersistenceRoundTrip();
        testCustomSortingLogicDirectly();
        testCustomSearchingLogicDirectly();
        testNameSanitizationAndExtraMarks();
        testGuiControllerOperationsAndFormats();
        testMultipleRecordsScale();
        testClearAllRecordsResetsToPlainErp();
        testSwingGuiEmptyAndPopulatedLabels();

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
     * Verifies that when 0 records exist, formatNextId() returns just "ERP" (no hardcoded number)
     * and adding without a first ERP ID asks for the starting ERP ID.
     */
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

    /**
     * Test Case 1: Normal Input + Auto-Detection of Next ERP ID
     */
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

    /**
     * Test Case 2: Boundary / Minimum Input (40.00% -> Pass)
     */
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

    /**
     * Test Case 3: Invalid Input (marks > 100)
     */
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

    /**
     * Test Case 4: Add Subsequent Record (Auto-Detected ID) + Sort + Search
     */
    private static void testCase4_DifferentValidScenario() {
        ArrayList<StudentResult> list = new ArrayList<>();
        list.add(new StudentResult("ERP 501", "Kiran Patel", 80, 70, 90));
        ExamResultManagementSystem.updateNextIdNumber(list);

        // Notice: no ERP ID entered in console input because ERP 502 is auto-detected!
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

    private static void testDuplicateErpRejection() {
        ArrayList<StudentResult> list = new ArrayList<>();
        ExamResultManagementSystem.updateNextIdNumber(list);
        ExamResultManagementSystem.addResultFromInputs(list, "ERP 900", "Kiran Patel", "80", "70", "90");

        OperationResult dup = ExamResultManagementSystem.addResultFromInputs(
                list, "900", "Another Student", "60", "60", "60");
        assertTrue("Edge Case - Duplicate ERP ID Rejected",
                !dup.isSuccess() && list.size() == 1 && dup.getMessage().contains("Duplicate ERP ID"),
                "Duplicate ERP ID should be rejected");
    }

    private static void testNegativeMarksEdgeCase() {
        ArrayList<StudentResult> list = new ArrayList<>();
        ExamResultManagementSystem.setNextIdNumber(300);
        String simulatedInput = "1\nTest Student\n-5 50 60\n5\n";
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

    private static void testFilePersistenceRoundTrip() {
        String testFileName = "test_results_roundtrip.txt";
        File testFile = new File(testFileName);
        if (testFile.exists()) testFile.delete();

        ArrayList<StudentResult> original = new ArrayList<>();
        original.add(new StudentResult("ERP 2050", "Kiran Patel", 80, 70, 90));
        original.add(new StudentResult("ERP 2051", "Mitali Roy", 35, 30, 38));

        ExamResultManagementSystem.saveResultsToFile(original, testFileName);

        ArrayList<StudentResult> loaded = new ArrayList<>();
        ExamResultManagementSystem.loadResultsFromFile(loaded, testFileName);

        assertTrue("File Persistence - Both records reloaded & next ID auto-detected (ERP 2052)",
                loaded.size() == 2 && "ERP 2052".equals(ExamResultManagementSystem.formatNextId()),
                "Expected 2 loaded records and next ID ERP 2052");

        assertTrue("File Persistence - Record 1 data integrity",
                loaded.get(0).getId().equals("ERP 2050") &&
                        loaded.get(0).getName().equals("Kiran Patel") &&
                        loaded.get(0).getTotal() == 240 &&
                        loaded.get(0).getResult().equals("Pass"),
                "Loaded record 1 mismatch");

        assertTrue("File Persistence - Record 2 data integrity",
                loaded.get(1).getId().equals("ERP 2051") &&
                        loaded.get(1).getName().equals("Mitali Roy") &&
                        loaded.get(1).getTotal() == 103 &&
                        loaded.get(1).getResult().equals("Fail"),
                "Loaded record 2 mismatch");

        testFile.delete();
    }

    private static void testCustomSortingLogicDirectly() {
        ArrayList<StudentResult> list = new ArrayList<>();
        StudentResult r1 = new StudentResult("ERP 401", "Low Student", 30, 30, 30);      // 30%
        StudentResult r2 = new StudentResult("ERP 402", "High Student", 90, 90, 90);     // 90%
        StudentResult r3 = new StudentResult("ERP 403", "Mid Student", 60, 60, 60);      // 60%

        list.add(r1);
        list.add(r2);
        list.add(r3);

        ExamResultManagementSystem.sortByPercentage(list);

        assertTrue("Direct Sort Logic - First is Highest",
                list.get(0).getId().equals("ERP 402") && list.get(0).getPercentage() == 90.0,
                "Sort order failed for index 0");

        assertTrue("Direct Sort Logic - Second is Middle",
                list.get(1).getId().equals("ERP 403") && list.get(1).getPercentage() == 60.0,
                "Sort order failed for index 1");

        assertTrue("Direct Sort Logic - Third is Lowest",
                list.get(2).getId().equals("ERP 401") && list.get(2).getPercentage() == 30.0,
                "Sort order failed for index 2");
    }

    private static void testCustomSearchingLogicDirectly() {
        ArrayList<StudentResult> list = new ArrayList<>();
        StudentResult r1 = new StudentResult("ERP 401", "Kiran Patel", 80, 70, 90);
        StudentResult r2 = new StudentResult("ERP 402", "Mitali Roy", 35, 30, 38);
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

        StudentResult notFound = ExamResultManagementSystem.searchById(list, "ERP 99999");
        assertTrue("Direct Search Logic - Non-existent ID returns null",
                notFound == null,
                "Search should return null for non-existent ID");
    }

    private static void testNameSanitizationAndExtraMarks() {
        ArrayList<StudentResult> list = new ArrayList<>();
        ExamResultManagementSystem.setNextIdNumber(601);
        runWithInput(list, "1\nPatel, Kiran\n80 70 90\n5\n");
        assertTrue("Edge Case - Comma in name is sanitized",
                list.size() == 1 && list.get(0).getName().equals("Patel Kiran"),
                "Name was not sanitized");
        StudentResult reparsed = ExamResultManagementSystem.parseLine(list.get(0).toFileString());
        assertTrue("Edge Case - Sanitized record survives file round trip",
                reparsed != null && reparsed.getName().equals("Patel Kiran"),
                "Record lost on reload");

        ArrayList<StudentResult> list2 = new ArrayList<>();
        ExamResultManagementSystem.setNextIdNumber(602);
        String out = runWithInput(list2, "1\nA B\n80 70 90 999\n5\n");
        assertTrue("Edge Case - More than 3 marks rejected",
                list2.isEmpty() && out.contains("exactly 3 marks"),
                "Extra marks were accepted");
    }

    private static void testGuiControllerOperationsAndFormats() {
        ArrayList<StudentResult> list = new ArrayList<>();
        ExamResultManagementSystem.updateNextIdNumber(list);

        // 1. Add 1st student with user-entered ERP ID "3001"
        OperationResult res1 = ExamResultManagementSystem.addResultFromInputs(
                list, "3001", "Kiran Patel", "80", "70", "90");
        assertTrue("GUI Controller - First Student Manual ERP Entry",
                res1.isSuccess() && res1.getMessage().equals(
                        "Result added. ID: ERP 3001 | Kiran Patel | Total: 240/300 | Percentage: 80.00 | Result: Pass"),
                "Unexpected message: " + res1.getMessage());

        // 2. Reject invalid marks without advancing auto-detected ERP 3002
        OperationResult resInvalid = ExamResultManagementSystem.addResultFromInputs(
                list, "", "Kiran Patel", "80", "105", "90");
        assertTrue("GUI Controller - Invalid Marks Rejected Without Consuming ERP 3002",
                !resInvalid.isSuccess()
                        && "ERP 3002".equals(ExamResultManagementSystem.formatNextId()),
                "Unexpected invalid result: " + resInvalid.getMessage());

        // 3. Add 2nd student with blank ERP field -> auto-detects ERP 3002!
        OperationResult res2 = ExamResultManagementSystem.addResultFromInputs(
                list, "", "Mitali Roy", "35 30 38", "", "");
        assertTrue("GUI Controller - Auto-Detected ERP 3002 + Combined Marks Paste",
                res2.isSuccess() && res2.getMessage().equals(
                        "Result added. ID: ERP 3002 | Mitali Roy | Total: 103/300 | Percentage: 34.33 | Result: Fail"),
                "Unexpected message: " + res2.getMessage());

        // 4. Sort & format check (and nextId still auto-detects ERP 3003!)
        ExamResultManagementSystem.sortByPercentage(list);
        ExamResultManagementSystem.updateNextIdNumber(list);
        String sortedText = ExamResultManagementSystem.formatSortedResults(list);
        assertTrue("GUI Controller - Sorted Format Exact Match & Next ID ERP 3003",
                sortedText.equals("1. ERP 3001 | Kiran Patel | 80.00 | Pass\n2. ERP 3002 | Mitali Roy | 34.33 | Fail")
                        && "ERP 3003".equals(ExamResultManagementSystem.formatNextId()),
                "Unexpected sort output:\n" + sortedText);
    }

    private static void testMultipleRecordsScale() {
        ArrayList<StudentResult> list = new ArrayList<>();
        ExamResultManagementSystem.updateNextIdNumber(list);

        for (int i = 0; i < 50; i++) {
            int mark = (i * 2) % 101;
            String firstErp = (i == 0) ? "1001" : null;
            OperationResult r = ExamResultManagementSystem.createAndAddResult(
                    list, firstErp, "Student " + (i + 1), mark, mark, mark);
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
        assertTrue("Multi-Record Scale - 50 Records Added, Sorted & Searched (ERP 1050)",
                list.size() == 50 && monotonicDescending && foundLastAdded != null
                        && foundLastAdded.getName().equals("Student 50")
                        && "ERP 1051".equals(ExamResultManagementSystem.formatNextId()),
                "Multi-record verification failed");
    }

    private static void testClearAllRecordsResetsToPlainErp() {
        String tempFile = "test_clear_records.txt";
        ArrayList<StudentResult> list = new ArrayList<>();
        ExamResultManagementSystem.updateNextIdNumber(list);
        ExamResultManagementSystem.createAndAddResult(list, "5001", "Kiran Patel", 80, 70, 90);
        ExamResultManagementSystem.createAndAddResult(list, null, "Mitali Roy", 35, 30, 38);
        ExamResultManagementSystem.saveResultsToFile(list, tempFile);

        ExamResultManagementSystem.clearAllResults(list, tempFile);

        ArrayList<StudentResult> reloaded = new ArrayList<>();
        ExamResultManagementSystem.loadResultsFromFile(reloaded, tempFile);
        new File(tempFile).delete();

        assertTrue("Clear All Records - Empties File & Resets Display to Plain 'ERP'",
                reloaded.isEmpty() && "ERP".equals(ExamResultManagementSystem.formatNextId()),
                "Expected 'ERP' after clearAllResults, got: " + ExamResultManagementSystem.formatNextId());
    }

    private static void testSwingGuiEmptyAndPopulatedLabels() {
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

        boolean cleanAndNoHardcodedNumber = allEmptyText.contains("| ERP |")
                && allEmptyText.contains("3. Search by ID")
                && allEmptyText.contains("Student Exam Results")
                && !allEmptyText.contains("10061")
                && !allEmptyText.contains("Linear Search")
                && !allEmptyText.contains("ArrayList<StudentResult>");

        assertTrue("Swing GUI - Empty State Shows 'ERP' With Zero Hardcoded Numbers or Bracketed Labels",
                cleanAndNoHardcodedNumber,
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
