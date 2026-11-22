import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Scanner;

/**
 * Class representing an individual student's exam result record.
 * Stored as an object in an ArrayList.
 */
class StudentResult {
    private String id;
    private String name;
    private int mark1;
    private int mark2;
    private int mark3;
    private int total;
    private double percentage;
    private String result;

    public StudentResult(String id, String name, int mark1, int mark2, int mark3) {
        this.id = id;
        this.name = name;
        this.mark1 = mark1;
        this.mark2 = mark2;
        this.mark3 = mark3;
        this.total = mark1 + mark2 + mark3;
        this.percentage = (this.total * 100.0) / 300.0;
        this.result = (this.percentage >= 40.0) ? "Pass" : "Fail";
    }

    public StudentResult(String id, String name, int mark1, int mark2, int mark3, int total, double percentage, String result) {
        this.id = id;
        this.name = name;
        this.mark1 = mark1;
        this.mark2 = mark2;
        this.mark3 = mark3;
        this.total = total;
        this.percentage = percentage;
        this.result = result;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getMark1() {
        return mark1;
    }

    public int getMark2() {
        return mark2;
    }

    public int getMark3() {
        return mark3;
    }

    public int getTotal() {
        return total;
    }

    public double getPercentage() {
        return percentage;
    }

    public String getResult() {
        return result;
    }

    /**
     * Formats full details matching the required specification:
     * e.g.: ER101 | Kiran Patel | Total: 240/300 | Percentage: 80.00 | Result: Pass
     */
    public String toFullDetailString() {
        return String.format(Locale.US, "%s | %s | Total: %d/300 | Percentage: %.2f | Result: %s",
                id, name, total, percentage, result);
    }

    /**
     * Formats concise details matching the sort output specification:
     * e.g.: ER101 | Kiran Patel | 80.00 | Pass
     */
    public String toSortString() {
        return String.format(Locale.US, "%s | %s | %.2f | %s",
                id, name, percentage, result);
    }

    /**
     * File storage format (CSV) preserving raw marks and computed metrics.
     */
    public String toFileString() {
        return String.format(Locale.US, "%s,%s,%d,%d,%d,%d,%.2f,%s",
                id, name, mark1, mark2, mark3, total, percentage, result);
    }

    @Override
    public String toString() {
        return toFullDetailString();
    }
}

/**
 * PS No. 48: Exam Result Management System
 * Console application to manage exam results using ArrayList, manual searching, manual sorting,
 * methods, and file handling.
 */
public class ExamResultManagementSystem {
    public static final String FILE_NAME = "results.txt";
    private static int nextIdNumber = 101;

    public static void main(String[] args) {
        ArrayList<StudentResult> resultsList = new ArrayList<>();

        // 1. Load data from file at startup
        loadResultsFromFile(resultsList, FILE_NAME);

        // Register shutdown hook to guarantee data is saved on program termination
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            saveResultsToFile(resultsList, FILE_NAME);
        }));

        Scanner scanner = new Scanner(System.in);
        boolean running = true;

        while (running) {
            printMenu();
            if (!scanner.hasNextLine()) {
                // End of input stream (EOF)
                break;
            }

            String input = scanner.nextLine().trim();
            if (input.isEmpty()) {
                continue;
            }

            int choice;
            try {
                choice = Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.println("Invalid choice. Please enter a valid menu number (1-5).");
                continue;
            }

            switch (choice) {
                case 1:
                    addResult(scanner, resultsList);
                    break;
                case 2:
                    displayAll(resultsList);
                    break;
                case 3:
                    handleSearch(scanner, resultsList);
                    break;
                case 4:
                    handleSort(resultsList);
                    break;
                case 5:
                    System.out.println("Exiting application. Saving data to " + FILE_NAME + "...");
                    saveResultsToFile(resultsList, FILE_NAME);
                    running = false;
                    break;
                default:
                    System.out.println("Invalid option. Please choose between 1 and 5.");
                    break;
            }
        }

        scanner.close();
    }

    /**
     * Displays the console menu.
     */
    public static void printMenu() {
        System.out.println("\nMenu: 1-Add Result, 2-Display All, 3-Search by ID, 4-Sort by Percentage (high to low), 5-Exit");
        System.out.print("Enter your choice: ");
    }

    /**
     * Feature 1: Add Result
     * Prompts for student name and marks in 3 subjects.
     * Validates that each mark is between 0 and 100.
     * Computes total, percentage, pass/fail status, and assigns sequential ID (starting ER101).
     */
    public static void addResult(Scanner scanner, ArrayList<StudentResult> list) {
        System.out.print("Enter Name: ");
        if (!scanner.hasNextLine()) return;
        String name = scanner.nextLine().trim();
        while (name.isEmpty() && scanner.hasNextLine()) {
            name = scanner.nextLine().trim();
        }
        // Commas / pipes are the file delimiters, so replace them to keep results.txt parseable
        name = name.replace(",", " ").replace("|", " ").replaceAll("\\s+", " ").trim();
        if (name.isEmpty()) {
            System.out.println("Invalid name. Name cannot be empty.");
            return;
        }

        System.out.print("Enter Marks (3 subjects separated by space): ");
        if (!scanner.hasNextLine()) return;
        String marksLine = scanner.nextLine().trim();
        while (marksLine.isEmpty() && scanner.hasNextLine()) {
            marksLine = scanner.nextLine().trim();
        }

        // Support spaces or commas between marks
        String[] tokens = marksLine.split("[,\\s]+");
        int m1, m2, m3;

        try {
            if (tokens.length > 3) {
                System.out.println("Invalid marks. Please enter exactly 3 marks.");
                return;
            } else if (tokens.length == 3) {
                m1 = Integer.parseInt(tokens[0]);
                m2 = Integer.parseInt(tokens[1]);
                m3 = Integer.parseInt(tokens[2]);
            } else {
                // If fewer than 3 marks on the first line, read remaining from subsequent lines
                int[] marks = new int[3];
                int idx = 0;
                for (String t : tokens) {
                    if (idx < 3) marks[idx++] = Integer.parseInt(t);
                }
                while (idx < 3 && scanner.hasNextLine()) {
                    String line = scanner.nextLine().trim();
                    if (line.isEmpty()) continue;
                    String[] lineTokens = line.split("[,\\s]+");
                    for (String t : lineTokens) {
                        if (idx < 3) marks[idx++] = Integer.parseInt(t);
                    }
                }
                m1 = marks[0];
                m2 = marks[1];
                m3 = marks[2];
            }
        } catch (NumberFormatException e) {
            System.out.println("Invalid marks. Each mark must be an integer between 0 and 100.");
            return;
        }

        // Validation: Marks in each of the 3 subjects must be between 0 and 100
        if (m1 < 0 || m1 > 100 || m2 < 0 || m2 > 100 || m3 < 0 || m3 > 100) {
            System.out.println("Invalid marks. Each mark must be between 0 and 100.");
            return;
        }

        // Generate ID
        String id = "ER" + nextIdNumber++;

        // Create student result object
        StudentResult newResult = new StudentResult(id, name, m1, m2, m3);
        list.add(newResult);

        // Required Expected Output:
        // Result added. ID: ER101 | Kiran Patel | Total: 240/300 | Percentage: 80.00 | Result: Pass
        System.out.println("Result added. ID: " + newResult.toFullDetailString());
    }

    /**
     * Feature 2: Display All
     * Iterates through the ArrayList and prints all student results.
     */
    public static void displayAll(ArrayList<StudentResult> list) {
        if (list.isEmpty()) {
            System.out.println("No results found.");
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            StudentResult r = list.get(i);
            System.out.println((i + 1) + ". " + r.toFullDetailString());
        }
    }

    /**
     * Handles Search UI interaction.
     */
    public static void handleSearch(Scanner scanner, ArrayList<StudentResult> list) {
        System.out.print("Enter Result ID to search: ");
        if (!scanner.hasNextLine()) return;
        String searchId = scanner.nextLine().trim();
        while (searchId.isEmpty() && scanner.hasNextLine()) {
            searchId = scanner.nextLine().trim();
        }

        StudentResult found = searchById(list, searchId);
        if (found != null) {
            // Required Expected Output:
            // Found: ER102 | Mitali Roy | Total: 103/300 | Percentage: 34.33 | Result: Fail
            System.out.println("Found: " + found.toFullDetailString());
        } else {
            System.out.println("Result not found for ID: " + searchId);
        }
    }

    /**
     * Feature 3: Search (by ID)
     * Manual Linear Search Algorithm (no built-in search methods).
     * Time Complexity: O(N), Space Complexity: O(1).
     */
    public static StudentResult searchById(ArrayList<StudentResult> list, String searchId) {
        if (list == null || searchId == null) {
            return null;
        }
        String target = searchId.trim();
        for (int i = 0; i < list.size(); i++) {
            StudentResult current = list.get(i);
            if (current.getId().equalsIgnoreCase(target)) {
                return current;
            }
        }
        return null;
    }

    /**
     * Handles Sort UI interaction and displays results sorted high to low by percentage.
     */
    public static void handleSort(ArrayList<StudentResult> list) {
        if (list.isEmpty()) {
            System.out.println("No results found.");
            return;
        }

        // Sort in-place using our custom sorting algorithm
        sortByPercentage(list);

        // Required Expected Output:
        // 1. ER101 | Kiran Patel | 80.00 | Pass
        // 2. ER102 | Mitali Roy | 34.33 | Fail
        for (int i = 0; i < list.size(); i++) {
            StudentResult r = list.get(i);
            System.out.println((i + 1) + ". " + r.toSortString());
        }
    }

    /**
     * Feature 4: Sort by Percentage (high to low)
     * Manual Bubble Sort Algorithm (no built-in sort methods).
     * Sorts descending by percentage.
     * Time Complexity: O(N^2), Space Complexity: O(1).
     */
    public static void sortByPercentage(ArrayList<StudentResult> list) {
        if (list == null || list.size() <= 1) {
            return;
        }
        int n = list.size();
        for (int i = 0; i < n - 1; i++) {
            boolean swapped = false;
            for (int j = 0; j < n - i - 1; j++) {
                // Descending order: if previous percentage < next percentage, swap
                if (list.get(j).getPercentage() < list.get(j + 1).getPercentage()) {
                    StudentResult temp = list.get(j);
                    list.set(j, list.get(j + 1));
                    list.set(j + 1, temp);
                    swapped = true;
                }
            }
            if (!swapped) {
                break;
            }
        }
    }

    /**
     * Feature 5: File Handling - Load data from file at start.
     * Robust parser supporting CSV, pipe-delimited, and test case formats.
     */
    public static void loadResultsFromFile(ArrayList<StudentResult> list, String fileName) {
        File file = new File(fileName);
        if (!file.exists()) {
            updateNextIdNumber(list);
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                StudentResult res = parseLine(line);
                if (res != null) {
                    list.add(res);
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading " + fileName + ": " + e.getMessage());
        }

        updateNextIdNumber(list);
    }

    /**
     * Parses a single line from results.txt into a StudentResult object.
     * Handles:
     * - CSV: ER101,Kiran Patel,80,70,90,240,80.00,Pass
     * - CSV: ER101,Kiran Patel,80,70,90
     * - Pipe with metrics: ER101 | Kiran Patel | Total: 240/300 | Percentage: 80.00 | Result: Pass
     * - Pipe concise: ER101 | Kiran Patel | 80.00 | Pass
     * - Pipe with marks: ER101 | Kiran Patel | 80 | 70 | 90
     */
    public static StudentResult parseLine(String line) {
        if (line == null) return null;
        line = line.trim();
        if (line.isEmpty() || line.startsWith("#")) return null;

        try {
            if (line.contains("|")) {
                String[] parts = line.split("\\|");
                for (int i = 0; i < parts.length; i++) {
                    parts[i] = parts[i].trim();
                }

                if (line.contains("Total:") && line.contains("Percentage:")) {
                    String id = parts[0];
                    String name = parts[1];
                    int total = 0;
                    double percentage = 0.0;
                    String result = "Pass";
                    for (String p : parts) {
                        if (p.startsWith("Total:")) {
                            String totalStr = p.replace("Total:", "").replace("/300", "").trim();
                            total = (int) Math.round(Double.parseDouble(totalStr));
                        } else if (p.startsWith("Percentage:")) {
                            String pctStr = p.replace("Percentage:", "").trim();
                            percentage = Double.parseDouble(pctStr);
                        } else if (p.startsWith("Result:")) {
                            result = p.replace("Result:", "").trim();
                        }
                    }
                    int m = total / 3;
                    int rem = total % 3;
                    return new StudentResult(id, name, m + (rem > 0 ? 1 : 0), m + (rem > 1 ? 1 : 0), m, total, percentage, result);
                }

                if (parts.length == 4) {
                    // ER101 | Kiran Patel | 80.00 | Pass
                    String id = parts[0];
                    String name = parts[1];
                    double percentage = Double.parseDouble(parts[2]);
                    String result = parts[3];
                    int total = (int) Math.round((percentage * 300.0) / 100.0);
                    int m = total / 3;
                    int rem = total % 3;
                    return new StudentResult(id, name, m + (rem > 0 ? 1 : 0), m + (rem > 1 ? 1 : 0), m, total, percentage, result);
                }

                if (parts.length == 5) {
                    // ER101 | Kiran Patel | 80 | 70 | 90
                    String id = parts[0];
                    String name = parts[1];
                    int m1 = Integer.parseInt(parts[2]);
                    int m2 = Integer.parseInt(parts[3]);
                    int m3 = Integer.parseInt(parts[4]);
                    return new StudentResult(id, name, m1, m2, m3);
                }

                if (parts.length >= 8) {
                    // ER101 | Kiran Patel | 80 | 70 | 90 | 240 | 80.00 | Pass
                    String id = parts[0];
                    String name = parts[1];
                    int m1 = Integer.parseInt(parts[2]);
                    int m2 = Integer.parseInt(parts[3]);
                    int m3 = Integer.parseInt(parts[4]);
                    int total = Integer.parseInt(parts[5]);
                    double percentage = Double.parseDouble(parts[6]);
                    String result = parts[7];
                    return new StudentResult(id, name, m1, m2, m3, total, percentage, result);
                }
            } else if (line.contains(",")) {
                String[] parts = line.split(",");
                for (int i = 0; i < parts.length; i++) {
                    parts[i] = parts[i].trim();
                }

                if (parts.length == 5) {
                    // ER101,Kiran Patel,80,70,90
                    String id = parts[0];
                    String name = parts[1];
                    int m1 = Integer.parseInt(parts[2]);
                    int m2 = Integer.parseInt(parts[3]);
                    int m3 = Integer.parseInt(parts[4]);
                    return new StudentResult(id, name, m1, m2, m3);
                } else if (parts.length >= 8) {
                    // ER101,Kiran Patel,80,70,90,240,80.00,Pass
                    String id = parts[0];
                    String name = parts[1];
                    int m1 = Integer.parseInt(parts[2]);
                    int m2 = Integer.parseInt(parts[3]);
                    int m3 = Integer.parseInt(parts[4]);
                    int total = Integer.parseInt(parts[5]);
                    double percentage = Double.parseDouble(parts[6]);
                    String result = parts[7];
                    return new StudentResult(id, name, m1, m2, m3, total, percentage, result);
                }
            }
        } catch (Exception ignored) {
            // Ignore malformed line and proceed
        }

        return null;
    }

    /**
     * Feature 5: File Handling - Save data to file on exit.
     */
    public static void saveResultsToFile(ArrayList<StudentResult> list, String fileName) {
        if (list == null) return;
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
            for (StudentResult r : list) {
                writer.write(r.toFileString());
                writer.newLine();
            }
        } catch (IOException e) {
            System.err.println("Error saving to " + fileName + ": " + e.getMessage());
        }
    }

    /**
     * Finds the maximum ID sequence number and updates nextIdNumber.
     * Ensures IDs start at ER101, and continue sequentially (ER102, ER103, ...).
     */
    public static void updateNextIdNumber(ArrayList<StudentResult> list) {
        int maxId = 100;
        if (list != null) {
            for (StudentResult r : list) {
                String id = r.getId();
                if (id != null && id.toUpperCase().startsWith("ER")) {
                    try {
                        int num = Integer.parseInt(id.substring(2).trim());
                        if (num > maxId) {
                            maxId = num;
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        nextIdNumber = maxId + 1;
    }

    /**
     * Getter for nextIdNumber (useful for testing).
     */
    public static int getNextIdNumber() {
        return nextIdNumber;
    }

    /**
     * Setter for nextIdNumber (useful for testing reset).
     */
    public static void setNextIdNumber(int val) {
        nextIdNumber = val;
    }
}
