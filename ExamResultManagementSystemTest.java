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
 * Automated test runner for ExamResultManagementSystem (Console + GUI controller + ERP 10061 IDs).
 * Validates all primary test cases, flexible ERP ID search, clean UI headings, and scale/persistence.
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
        testLegacyIdUpgradeOnLoad();
        testCustomSortingLogicDirectly();
        testCustomSearchingLogicDirectly();
        testNameSanitizationAndExtraMarks();
        testGuiControllerOperationsAndFormats();
        testMultipleRecordsScale();
        testClearAllRecordsAndResetId();
        testSwingGuiInstantiationAndCleanLabels();

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
     * Menu option 1 (Add result) | Name: Kiran Patel | Marks: 80 70 90
     * Expected: Result added. ID: ERP 10061 | Kiran Patel | Total: 240/300 | Percentage: 80.00 | Result: Pass
     */
    private static void testCase1_NormalInput() {
        cleanResultsFile();
        ExamResultManagementSystem.setNextIdNumber(10061);
        ArrayList<StudentResult> list = new ArrayList<>();

        String simulatedInput = "1\nKiran Patel\n80 70 90\n5\n";
        String output = runWithInput(list, simulatedInput);

        String expected = "Result added. ID: ERP 10061 | Kiran Patel | Total: 240/300 | Percentage: 80.00 | Result: Pass";
        assertTrue("Test Case 1 - Normal Input Output Match",
                output.contains(expected),
                "Expected output to contain: [" + expected + "], but got:\n" + output);

        assertTrue("Test Case 1 - Record Stored in ArrayList with ERP 10061",
                list.size() == 1 && list.get(0).getId().equals("ERP 10061") && list.get(0).getTotal() == 240,
                "List record mismatch");
    }

    /**
     * Test Case 2: Boundary / Minimum Input
     * Menu option 1 (Add result) | Name: Lalit Soni | Marks: 40 40 40
     * Expected: Result added. ID: ERP 10061 | Lalit Soni | Total: 120/300 | Percentage: 40.00 | Result: Pass
     */
    private static void testCase2_BoundaryMinimumInput() {
        cleanResultsFile();
        ExamResultManagementSystem.setNextIdNumber(10061);
        ArrayList<StudentResult> list = new ArrayList<>();

        String simulatedInput = "1\nLalit Soni\n40 40 40\n5\n";
        String output = runWithInput(list, simulatedInput);

        String expected = "Result added. ID: ERP 10061 | Lalit Soni | Total: 120/300 | Percentage: 40.00 | Result: Pass";
        assertTrue("Test Case 2 - Boundary / Minimum Input Output Match",
                output.contains(expected),
                "Expected output to contain: [" + expected + "], but got:\n" + output);

        assertTrue("Test Case 2 - Percentage is 40.00 and Pass",
                list.size() == 1 && list.get(0).getPercentage() == 40.0 && list.get(0).getResult().equals("Pass"),
                "Boundary condition failed");
    }

    /**
     * Test Case 3: Invalid Input
     * Menu option 1 (Add result) | Name: Kiran Patel | Marks: 80 105 90
     * Expected: Invalid marks. Each mark must be between 0 and 100.
     */
    private static void testCase3_InvalidInput() {
        cleanResultsFile();
        ExamResultManagementSystem.setNextIdNumber(10061);
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

        assertTrue("Test Case 3 - ID ERP 10061 Not Consumed",
                ExamResultManagementSystem.getNextIdNumber() == 10061,
                "ID counter should not be incremented on invalid input");
    }

    /**
     * Test Case 4: Different Valid Scenario
     */
    private static void testCase4_DifferentValidScenario() {
        cleanResultsFile();
        ArrayList<StudentResult> list = new ArrayList<>();
        list.add(new StudentResult("ERP 10061", "Kiran Patel", 80, 70, 90));
        ExamResultManagementSystem.updateNextIdNumber(list);

        String simulatedInput = "1\nMitali Roy\n35 30 38\n4\n3\nERP 10062\n5\n";
        String output = runWithInput(list, simulatedInput);

        String expectedA = "Result added. ID: ERP 10062 | Mitali Roy | Total: 103/300 | Percentage: 34.33 | Result: Fail";
        String expectedB1 = "1. ERP 10061 | Kiran Patel | 80.00 | Pass";
        String expectedB2 = "2. ERP 10062 | Mitali Roy | 34.33 | Fail";
        String expectedC = "Found: ERP 10062 | Mitali Roy | Total: 103/300 | Percentage: 34.33 | Result: Fail";

        assertTrue("Test Case 4(a) - Add Mitali Roy Fail Output Match",
                output.contains(expectedA),
                "Missing or incorrect 4(a) output. Expected: " + expectedA);

        assertTrue("Test Case 4(b) - Sort by Percentage (high to low) Output Match",
                output.contains(expectedB1) && output.contains(expectedB2),
                "Missing or incorrect 4(b) output. Expected:\n" + expectedB1 + "\n" + expectedB2);

        assertTrue("Test Case 4(c) - Search by ID (ERP 10062) Output Match",
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
        list.add(new StudentResult("ERP 10061", "Kiran Patel", 80, 70, 90));
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
        original.add(new StudentResult("ERP 10061", "Kiran Patel", 80, 70, 90));
        original.add(new StudentResult("ERP 10062", "Mitali Roy", 35, 30, 38));

        ExamResultManagementSystem.saveResultsToFile(original, testFileName);

        ArrayList<StudentResult> loaded = new ArrayList<>();
        ExamResultManagementSystem.loadResultsFromFile(loaded, testFileName);

        assertTrue("File Persistence - Both records reloaded",
                loaded.size() == 2,
                "Expected 2 loaded records");

        assertTrue("File Persistence - Record 1 data integrity",
                loaded.get(0).getId().equals("ERP 10061") &&
                        loaded.get(0).getName().equals("Kiran Patel") &&
                        loaded.get(0).getTotal() == 240 &&
                        loaded.get(0).getResult().equals("Pass"),
                "Loaded record 1 mismatch");

        assertTrue("File Persistence - Record 2 data integrity",
                loaded.get(1).getId().equals("ERP 10062") &&
                        loaded.get(1).getName().equals("Mitali Roy") &&
                        loaded.get(1).getTotal() == 103 &&
                        loaded.get(1).getResult().equals("Fail"),
                "Loaded record 2 mismatch");

        testFile.delete();
    }

    private static void testLegacyIdUpgradeOnLoad() {
        StudentResult upgraded = ExamResultManagementSystem.parseLine("ER101,Kiran Patel,80,70,90,240,80.00,Pass");
        assertTrue("Legacy File Upgrade - ER101 upgrades cleanly to ERP 10061",
                upgraded != null && "ERP 10061".equals(upgraded.getId()),
                "Expected ERP 10061, got: " + (upgraded == null ? "null" : upgraded.getId()));
    }

    private static void testCustomSortingLogicDirectly() {
        ArrayList<StudentResult> list = new ArrayList<>();
        StudentResult r1 = new StudentResult("ERP 10061", "Low Student", 30, 30, 30);      // 30%
        StudentResult r2 = new StudentResult("ERP 10062", "High Student", 90, 90, 90);     // 90%
        StudentResult r3 = new StudentResult("ERP 10063", "Mid Student", 60, 60, 60);      // 60%

        list.add(r1);
        list.add(r2);
        list.add(r3);

        ExamResultManagementSystem.sortByPercentage(list);

        assertTrue("Direct Sort Logic - First is Highest",
                list.get(0).getId().equals("ERP 10062") && list.get(0).getPercentage() == 90.0,
                "Sort order failed for index 0");

        assertTrue("Direct Sort Logic - Second is Middle",
                list.get(1).getId().equals("ERP 10063") && list.get(1).getPercentage() == 60.0,
                "Sort order failed for index 1");

        assertTrue("Direct Sort Logic - Third is Lowest",
                list.get(2).getId().equals("ERP 10061") && list.get(2).getPercentage() == 30.0,
                "Sort order failed for index 2");
    }

    private static void testCustomSearchingLogicDirectly() {
        ArrayList<StudentResult> list = new ArrayList<>();
        StudentResult r1 = new StudentResult("ERP 10061", "Kiran Patel", 80, 70, 90);
        StudentResult r2 = new StudentResult("ERP 10062", "Mitali Roy", 35, 30, 38);
        list.add(r1);
        list.add(r2);

        StudentResult foundExact = ExamResultManagementSystem.searchById(list, "ERP 10062");
        assertTrue("Direct Search Logic - Exact Match (ERP 10062)",
                foundExact != null && foundExact.getName().equals("Mitali Roy"),
                "Search failed for exact match");

        StudentResult foundNoSpaceLower = ExamResultManagementSystem.searchById(list, "erp10061");
        assertTrue("Direct Search Logic - Case & Space Insensitive Match (erp10061)",
                foundNoSpaceLower != null && foundNoSpaceLower.getName().equals("Kiran Patel"),
                "Search failed for erp10061");

        StudentResult foundDigitsOnly = ExamResultManagementSystem.searchById(list, "10062");
        assertTrue("Direct Search Logic - Numeric Shorthand Match (10062)",
                foundDigitsOnly != null && foundDigitsOnly.getName().equals("Mitali Roy"),
                "Search failed for numeric shorthand 10062");

        StudentResult notFound = ExamResultManagementSystem.searchById(list, "ERP 99999");
        assertTrue("Direct Search Logic - Non-existent ID returns null",
                notFound == null,
                "Search should return null for non-existent ID");
    }

    private static void testNameSanitizationAndExtraMarks() {
        ExamResultManagementSystem.setNextIdNumber(10061);
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

    /**
     * Tests the shared GUI controller methods (3-box input, combined input, invalid marks, sort & search formatting).
     */
    private static void testGuiControllerOperationsAndFormats() {
        ExamResultManagementSystem.setNextIdNumber(10061);
        ArrayList<StudentResult> list = new ArrayList<>();

        // 1. Add via 3 separate subject fields
        OperationResult res1 = ExamResultManagementSystem.addResultFromInputs(list, "Kiran Patel", "80", "70", "90");
        assertTrue("GUI Controller - 3 Subject Fields Add Result",
                res1.isSuccess() && res1.getMessage().equals(
                        "Result added. ID: ERP 10061 | Kiran Patel | Total: 240/300 | Percentage: 80.00 | Result: Pass"),
                "Unexpected message: " + res1.getMessage());

        // 2. Reject invalid marks via GUI controller without consuming ERP 10062
        OperationResult resInvalid = ExamResultManagementSystem.addResultFromInputs(list, "Kiran Patel", "80", "105", "90");
        assertTrue("GUI Controller - Invalid Marks Rejected with Exact Message",
                !resInvalid.isSuccess() && resInvalid.getMessage().equals(
                        "Invalid marks. Each mark must be between 0 and 100.")
                        && ExamResultManagementSystem.getNextIdNumber() == 10062,
                "Unexpected invalid result: " + resInvalid.getMessage());

        // 3. Add via pasted space-separated marks in Subject 1 field ("35 30 38")
        OperationResult res2 = ExamResultManagementSystem.addResultFromInputs(list, "Mitali Roy", "35 30 38", "", "");
        assertTrue("GUI Controller - Combined Marks Paste Support",
                res2.isSuccess() && res2.getMessage().equals(
                        "Result added. ID: ERP 10062 | Mitali Roy | Total: 103/300 | Percentage: 34.33 | Result: Fail"),
                "Unexpected message: " + res2.getMessage());

        // 4. Sort & format check
        ExamResultManagementSystem.sortByPercentage(list);
        String sortedText = ExamResultManagementSystem.formatSortedResults(list);
        assertTrue("GUI Controller - Sorted Format Exact Match",
                sortedText.equals("1. ERP 10061 | Kiran Patel | 80.00 | Pass\n2. ERP 10062 | Mitali Roy | 34.33 | Fail"),
                "Unexpected sort output:\n" + sortedText);

        // 5. Index lookup check for JTable row highlighting
        int idx = ExamResultManagementSystem.findIndexById(list, "erp 10062");
        assertTrue("GUI Controller - Linear Index Lookup",
                idx == 1,
                "Expected index 1 for ERP 10062 after sort, got " + idx);
    }

    /**
     * Verifies handling of multiple records (50 students): add, sort high-to-low, and search.
     */
    private static void testMultipleRecordsScale() {
        ExamResultManagementSystem.setNextIdNumber(10061);
        ArrayList<StudentResult> list = new ArrayList<>();

        for (int i = 0; i < 50; i++) {
            int mark = (i * 2) % 101;
            OperationResult r = ExamResultManagementSystem.createAndAddResult(
                    list, "Student " + (i + 1), mark, mark, mark);
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

        StudentResult foundLastAdded = ExamResultManagementSystem.searchById(list, "ERP 10110");
        assertTrue("Multi-Record Scale - 50 Records Added, Sorted & Searched (ERP 10110)",
                list.size() == 50 && monotonicDescending && foundLastAdded != null
                        && foundLastAdded.getName().equals("Student 50"),
                "Multi-record verification failed");
    }

    /**
     * Verifies clearing all saved records wipes results.txt, empties ArrayList, and resets ID to ERP 10061.
     */
    private static void testClearAllRecordsAndResetId() {
        String tempFile = "test_clear_records.txt";
        ExamResultManagementSystem.setNextIdNumber(10061);
        ArrayList<StudentResult> list = new ArrayList<>();
        ExamResultManagementSystem.createAndAddResult(list, "Kiran Patel", 80, 70, 90);
        ExamResultManagementSystem.createAndAddResult(list, "Mitali Roy", 35, 30, 38);
        ExamResultManagementSystem.saveResultsToFile(list, tempFile);

        ExamResultManagementSystem.clearAllResults(list, tempFile);

        ArrayList<StudentResult> reloaded = new ArrayList<>();
        ExamResultManagementSystem.loadResultsFromFile(reloaded, tempFile);

        OperationResult fresh = ExamResultManagementSystem.createAndAddResult(list, "Lalit Soni", 40, 40, 40);
        new File(tempFile).delete();

        assertTrue("Clear All Records - Empties File & Resets ID to ERP 10061",
                reloaded.isEmpty() && fresh.isSuccess() && fresh.getStudentResult().getId().equals("ERP 10061"),
                "Failed to clear saved records and reset ID to ERP 10061");
    }

    /**
     * Verifies that the Swing Desktop GUI constructs cleanly and contains clean headings
     * without "(Linear Search)" or "(ArrayList<StudentResult>)".
     */
    private static void testSwingGuiInstantiationAndCleanLabels() {
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            assertTrue("Swing GUI - Headless Safe Fallback", true, "Skipped window creation in headless mode");
            return;
        }
        ExamResultManagementSystem.setNextIdNumber(10061);
        ArrayList<StudentResult> list = new ArrayList<>();
        ExamResultManagementSystem.createAndAddResult(list, "Kiran Patel", 80, 70, 90);
        ExamResultManagementSystem.createAndAddResult(list, "Mitali Roy", 35, 30, 38);

        ExamResultGUI gui = new ExamResultGUI(list, "test_gui_smoke.txt");
        StringBuilder uiTexts = new StringBuilder();
        collectComponentTexts(gui.getContentPane(), uiTexts);
        String allText = uiTexts.toString();

        boolean validWindow = gui.getTitle().contains("Exam Result Management System")
                && gui.getWidth() >= 1000;
        boolean cleanLabels = allText.contains("3. Search by ID")
                && allText.contains("Student Exam Results")
                && !allText.contains("Linear Search")
                && !allText.contains("ArrayList<StudentResult>");

        gui.dispose();
        new File("test_gui_smoke.txt").delete();

        assertTrue("Swing GUI - Window Constructed Cleanly",
                validWindow,
                "GUI window failed sanity check");
        assertTrue("Swing GUI - Clean Headings Without Bracketed Technical Notations",
                cleanLabels,
                "Found unwanted technical notation in UI text: " + allText);
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

    private static void cleanResultsFile() {
        // Intentionally does NOT touch the real results.txt: tests run fully in memory.
    }
}
