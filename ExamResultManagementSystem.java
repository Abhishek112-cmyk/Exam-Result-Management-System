import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Scanner;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;

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
     * Formats full details:
     * e.g.: ERP 501 | Kiran Patel | Total: 240/300 | Percentage: 80.00 | Result: Pass
     */
    public String toFullDetailString() {
        return String.format(Locale.US, "%s | %s | Total: %d/300 | Percentage: %.2f | Result: %s",
                id, name, total, percentage, result);
    }

    /**
     * Formats concise details for sorted output:
     * e.g.: ERP 501 | Kiran Patel | 80.00 | Pass
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

    /**
     * Serializes this record as a JSON object for the local HTTP server.
     */
    public String toJsonString() {
        return String.format(
                Locale.US,
                "{\"id\":\"%s\",\"name\":\"%s\",\"mark1\":%d,\"mark2\":%d,\"mark3\":%d,\"total\":%d,\"percentage\":%.2f,\"result\":\"%s\"}",
                escapeJson(id), escapeJson(name), mark1, mark2, mark3, total, percentage, escapeJson(result));
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Override
    public String toString() {
        return toFullDetailString();
    }
}

/**
 * Outcome wrapper for shared validation and result creation across Console, Swing GUI, and Web API.
 */
class OperationResult {
    private final boolean success;
    private final String message;
    private final StudentResult studentResult;

    public OperationResult(boolean success, String message, StudentResult studentResult) {
        this.success = success;
        this.message = message;
        this.studentResult = studentResult;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public StudentResult getStudentResult() {
        return studentResult;
    }
}

/**
 * PS No. 48: Exam Result Management System
 * Supports Console mode (default), Smooth Swing Desktop GUI (--gui), and Local Web Backend (--server)
 * using ArrayList, manual Linear Search, manual Bubble Sort, methods, and file handling (results.txt).
 */
public class ExamResultManagementSystem {
    public static final String FILE_NAME = "results.txt";
    public static final String ID_PREFIX = "ERP ";
    private static Integer nextIdNumber = null;

    public static void main(String[] args) {
        boolean launchGui = false;
        boolean launchServer = false;
        int port = 8080;

        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if ("--gui".equalsIgnoreCase(arg) || "-g".equalsIgnoreCase(arg) || "gui".equalsIgnoreCase(arg)) {
                    launchGui = true;
                } else if ("--server".equalsIgnoreCase(arg) || "server".equalsIgnoreCase(arg)) {
                    launchServer = true;
                } else if (arg.startsWith("--port=")) {
                    try {
                        port = Integer.parseInt(arg.substring("--port=".length()));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }

        if (launchServer) {
            startWebServer(port);
        } else if (launchGui) {
            launchGuiApp();
        } else {
            runConsoleApp();
        }
    }

    public static String formatId(int num) {
        return ID_PREFIX + num;
    }

    public static String formatNextId() {
        return (nextIdNumber == null) ? "ERP" : formatId(nextIdNumber);
    }

    public static String parseErpInput(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return null;
        String digits = trimmed.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return null;
        try {
            int num = Integer.parseInt(digits);
            if (num < 0) return null;
            return formatId(num);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Normalizes an ERP ID string so "ERP <n>", "ERP<n>", "erp <n>", and "<n>" match cleanly.
     */
    public static String normalizeIdKey(String raw) {
        if (raw == null) return "";
        String cleaned = raw.trim().toUpperCase(Locale.US).replaceAll("\\s+", "");
        if (cleaned.startsWith("ERP")) {
            return "ERP" + cleaned.substring(3);
        }
        if (cleaned.startsWith("ER")) {
            return "ERP" + cleaned.substring(2);
        }
        if (cleaned.matches("\\d+")) {
            return "ERP" + cleaned;
        }
        return cleaned;
    }

    /**
     * Launches the interactive Console application.
     */
    public static void runConsoleApp() {
        ArrayList<StudentResult> resultsList = new ArrayList<>();
        loadResultsFromFile(resultsList, FILE_NAME);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            saveResultsToFile(resultsList, FILE_NAME);
        }));

        Scanner scanner = new Scanner(System.in);
        boolean running = true;

        while (running) {
            printMenu();
            if (!scanner.hasNextLine()) {
                break;
            }

            String input = scanner.nextLine().trim();
            if (input.isEmpty()) {
                continue;
            }

            if ("gui".equalsIgnoreCase(input)) {
                launchGuiWithExistingList(resultsList);
                break;
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
     * Launches the smooth Java Swing Desktop GUI application.
     */
    public static void launchGuiApp() {
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("Graphical display is not available in headless mode. Starting console mode instead.");
            runConsoleApp();
            return;
        }

        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");

        ArrayList<StudentResult> resultsList = new ArrayList<>();
        loadResultsFromFile(resultsList, FILE_NAME);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            saveResultsToFile(resultsList, FILE_NAME);
        }));

        launchGuiWithExistingList(resultsList);
    }

    private static void launchGuiWithExistingList(ArrayList<StudentResult> resultsList) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            } catch (Exception ignored) {
            }
            Toolkit.getDefaultToolkit().setDynamicLayout(true);
            ExamResultGUI gui = new ExamResultGUI(resultsList, FILE_NAME);
            gui.setVisible(true);
        });
    }

    /**
     * Starts a lightweight pure-Java HTTP Server serving the upgraded Web Frontend (public/)
     * and the /api/results JSON endpoints backed directly by ArrayList<StudentResult> and results.txt.
     */
    public static void startWebServer(int port) {
        ArrayList<StudentResult> resultsList = new ArrayList<>();
        loadResultsFromFile(resultsList, FILE_NAME);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            synchronized (resultsList) {
                saveResultsToFile(resultsList, FILE_NAME);
            }
        }));

        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/api/results", exchange -> handleApiExchange(exchange, resultsList));
            server.createContext("/", ExamResultManagementSystem::handleStaticExchange);
            server.setExecutor(null);
            server.start();
            System.out.println("============================================================");
            System.out.println("  Exam Result Management System Web Server Running");
            System.out.println("  Open in browser: http://localhost:" + port);
            System.out.println("  Persistence file: " + FILE_NAME);
            System.out.println("============================================================");
        } catch (IOException e) {
            System.err.println("Failed to start HTTP server on port " + port + ": " + e.getMessage());
        }
    }

    private static void handleApiExchange(HttpExchange exchange, ArrayList<StudentResult> list) throws IOException {
        String method = exchange.getRequestMethod();
        String responseJson;
        int status = 200;

        synchronized (list) {
            if ("GET".equalsIgnoreCase(method)) {
                responseJson = buildStateJson(list, true, "Ready.", null, -1);
            } else if ("POST".equalsIgnoreCase(method)) {
                String body = readBody(exchange.getRequestBody());
                String action = extractJsonString(body, "action");

                if ("add".equalsIgnoreCase(action)) {
                    String erpId = extractJsonString(body, "erpId");
                    String name = extractJsonString(body, "name");
                    String m1 = extractJsonString(body, "m1");
                    String m2 = extractJsonString(body, "m2");
                    String m3 = extractJsonString(body, "m3");
                    OperationResult op = addResultFromInputs(list, erpId, name, m1, m2, m3);
                    if (op.isSuccess()) {
                        saveResultsToFile(list, FILE_NAME);
                    } else {
                        status = 400;
                    }
                    responseJson = buildStateJson(list, op.isSuccess(), op.getMessage(), op.getStudentResult(), list.size() - 1);
                } else if ("search".equalsIgnoreCase(action)) {
                    String query = extractJsonString(body, "id");
                    StudentResult found = searchById(list, query);
                    int idx = findIndexById(list, query);
                    if (found != null) {
                        responseJson = buildStateJson(list, true, "Found: " + found.toFullDetailString(), found, idx);
                    } else {
                        responseJson = buildStateJson(list, false, "Result not found for ID: " + query, null, -1);
                    }
                } else if ("sort".equalsIgnoreCase(action)) {
                    if (list.isEmpty()) {
                        responseJson = buildStateJson(list, false, "No results found.", null, -1);
                    } else {
                        sortByPercentage(list);
                        saveResultsToFile(list, FILE_NAME);
                        responseJson = buildStateJson(list, true, formatSortedResults(list), list.get(0), 0);
                    }
                } else if ("clear".equalsIgnoreCase(action)) {
                    clearAllResults(list, FILE_NAME);
                    responseJson = buildStateJson(list, true,
                            "Cleared all saved records. Auto-detected ERP ID reset to ERP.", null, -1);
                } else {
                    status = 400;
                    responseJson = "{\"success\":false,\"message\":\"Unsupported action.\"}";
                }
            } else {
                status = 405;
                responseJson = "{\"success\":false,\"message\":\"Method not allowed.\"}";
            }
        }

        byte[] bytes = responseJson.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String buildStateJson(
            ArrayList<StudentResult> list, boolean success, String message, StudentResult focus, int matchedIndex) {
        int passCount = 0;
        StringBuilder arr = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) arr.append(",");
            StudentResult r = list.get(i);
            arr.append(r.toJsonString());
            if ("Pass".equalsIgnoreCase(r.getResult())) passCount++;
        }
        arr.append("]");
        int failCount = list.size() - passCount;
        String safeMsg = message == null ? "" : message.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        String focusJson = (focus == null) ? "null" : focus.toJsonString();

        return String.format(
                Locale.US,
                "{\"success\":%b,\"message\":\"%s\",\"nextId\":\"%s\",\"matchedIndex\":%d,\"record\":%s,"
                        + "\"stats\":{\"total\":%d,\"pass\":%d,\"fail\":%d},\"records\":%s}",
                success, safeMsg, formatNextId(), matchedIndex, focusJson,
                list.size(), passCount, failCount, arr.toString());
    }

    private static String readBody(InputStream is) throws IOException {
        byte[] buf = is.readAllBytes();
        return new String(buf, StandardCharsets.UTF_8);
    }

    private static String extractJsonString(String json, String key) {
        if (json == null || key == null) return "";
        String needle = "\"" + key + "\"";
        int kIdx = json.indexOf(needle);
        if (kIdx == -1) return "";
        int colon = json.indexOf(':', kIdx + needle.length());
        if (colon == -1) return "";
        int i = colon + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
        if (i >= json.length()) return "";
        if (json.charAt(i) == '"') {
            StringBuilder sb = new StringBuilder();
            i++;
            while (i < json.length()) {
                char c = json.charAt(i++);
                if (c == '\\' && i < json.length()) {
                    sb.append(json.charAt(i++));
                } else if (c == '"') {
                    break;
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        } else {
            int start = i;
            while (i < json.length() && ",}".indexOf(json.charAt(i)) == -1) i++;
            return json.substring(start, i).trim();
        }
    }

    private static void handleStaticExchange(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path == null || "/".equals(path)) path = "/index.html";
        File publicDir = new File("public").getCanonicalFile();
        File target = new File(publicDir, path.substring(1)).getCanonicalFile();

        if (!target.getPath().startsWith(publicDir.getPath()) || !target.isFile()) {
            target = new File(publicDir, "index.html");
        }
        if (!target.isFile()) {
            byte[] notFound = "Frontend files not found in public/".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(404, notFound.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(notFound);
            }
            return;
        }

        String name = target.getName().toLowerCase(Locale.US);
        String mime = "text/plain; charset=utf-8";
        if (name.endsWith(".html")) mime = "text/html; charset=utf-8";
        else if (name.endsWith(".css")) mime = "text/css; charset=utf-8";
        else if (name.endsWith(".js")) mime = "application/javascript; charset=utf-8";
        else if (name.endsWith(".svg")) mime = "image/svg+xml";
        else if (name.endsWith(".json")) mime = "application/json; charset=utf-8";

        byte[] content = Files.readAllBytes(target.toPath());
        exchange.getResponseHeaders().set("Content-Type", mime);
        exchange.sendResponseHeaders(200, content.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(content);
        }
    }

    /**
     * Displays the console menu.
     */
    public static void printMenu() {
        System.out.println("\nMenu: 1-Add Result, 2-Display All, 3-Search by ID, 4-Sort by Percentage (high to low), 5-Exit");
        System.out.print("Enter your choice: ");
    }

    /**
     * Shared core method to validate ERP ID (manual first ID or auto-detected next ID),
     * sanitize name, validate 3 subject marks (0-100), and append to the ArrayList.
     */
    public static OperationResult createAndAddResult(
            ArrayList<StudentResult> list, String rawErpId, String rawName, int m1, int m2, int m3) {
        String resolvedId;
        if (rawErpId != null && !rawErpId.trim().isEmpty() && !"ERP".equalsIgnoreCase(rawErpId.trim())) {
            resolvedId = parseErpInput(rawErpId);
            if (resolvedId == null) {
                return new OperationResult(false, "Invalid ERP ID. Please enter a valid ERP number.", null);
            }
        } else {
            if (nextIdNumber == null) {
                updateNextIdNumber(list);
            }
            if (nextIdNumber == null) {
                return new OperationResult(
                        false,
                        "Please enter the first ERP ID so the system can auto-detect subsequent ERP IDs.",
                        null);
            }
            resolvedId = formatId(nextIdNumber);
        }

        if (searchById(list, resolvedId) != null) {
            return new OperationResult(
                    false,
                    "Duplicate ERP ID (" + resolvedId + "). A record with this ID already exists.",
                    null);
        }

        if (rawName == null) {
            return new OperationResult(false, "Invalid name. Name cannot be empty.", null);
        }
        String name = rawName.replace(",", " ").replace("|", " ").replaceAll("\\s+", " ").trim();
        if (name.isEmpty()) {
            return new OperationResult(false, "Invalid name. Name cannot be empty.", null);
        }

        if (m1 < 0 || m1 > 100 || m2 < 0 || m2 > 100 || m3 < 0 || m3 > 100) {
            return new OperationResult(false, "Invalid marks. Each mark must be between 0 and 100.", null);
        }

        StudentResult newResult = new StudentResult(resolvedId, name, m1, m2, m3);
        list.add(newResult);
        updateNextIdNumber(list);

        String message = "Result added. ID: " + newResult.toFullDetailString();
        return new OperationResult(true, message, newResult);
    }

    public static OperationResult createAndAddResult(ArrayList<StudentResult> list, String rawName, int m1, int m2, int m3) {
        return createAndAddResult(list, null, rawName, m1, m2, m3);
    }

    /**
     * Parses raw mark strings (either 3 separate inputs or space/comma-separated marks in the first input)
     * and adds the student result.
     */
    public static OperationResult addResultFromInputs(
            ArrayList<StudentResult> list, String rawErpId, String rawName, String s1, String s2, String s3) {
        if (rawErpId == null || rawErpId.trim().isEmpty() || "ERP".equalsIgnoreCase(rawErpId.trim())) {
            if (nextIdNumber == null) {
                updateNextIdNumber(list);
            }
            if (nextIdNumber == null) {
                return new OperationResult(
                        false,
                        "Please enter the first ERP ID so the system can auto-detect subsequent ERP IDs.",
                        null);
            }
        } else if (parseErpInput(rawErpId) == null) {
            return new OperationResult(false, "Invalid ERP ID. Please enter a valid ERP number.", null);
        }

        String nameCheck = (rawName == null) ? "" : rawName.replace(",", " ").replace("|", " ").replaceAll("\\s+", " ").trim();
        if (nameCheck.isEmpty()) {
            return new OperationResult(false, "Invalid name. Name cannot be empty.", null);
        }

        String p1 = (s1 == null) ? "" : s1.trim();
        String p2 = (s2 == null) ? "" : s2.trim();
        String p3 = (s3 == null) ? "" : s3.trim();

        int m1, m2, m3;
        try {
            if (!p1.isEmpty() && p2.isEmpty() && p3.isEmpty() && p1.matches(".*[,\\s]+.*")) {
                String[] tokens = p1.split("[,\\s]+");
                if (tokens.length != 3) {
                    return new OperationResult(false, "Invalid marks. Please enter exactly 3 marks.", null);
                }
                m1 = Integer.parseInt(tokens[0]);
                m2 = Integer.parseInt(tokens[1]);
                m3 = Integer.parseInt(tokens[2]);
            } else {
                if (p1.isEmpty() || p2.isEmpty() || p3.isEmpty()) {
                    return new OperationResult(false, "Invalid marks. Please enter marks for all 3 subjects.", null);
                }
                if (p1.matches(".*[,\\s]+.*") || p2.matches(".*[,\\s]+.*") || p3.matches(".*[,\\s]+.*")) {
                    return new OperationResult(false, "Invalid marks. Please enter exactly 3 marks.", null);
                }
                m1 = Integer.parseInt(p1);
                m2 = Integer.parseInt(p2);
                m3 = Integer.parseInt(p3);
            }
        } catch (NumberFormatException e) {
            return new OperationResult(false, "Invalid marks. Each mark must be an integer between 0 and 100.", null);
        }

        return createAndAddResult(list, rawErpId, nameCheck, m1, m2, m3);
    }

    public static OperationResult addResultFromInputs(ArrayList<StudentResult> list, String rawName, String s1, String s2, String s3) {
        return addResultFromInputs(list, null, rawName, s1, s2, s3);
    }

    /**
     * Feature 1: Add Result (Console interaction)
     */
    public static void addResult(Scanner scanner, ArrayList<StudentResult> list) {
        String rawErp = null;
        if (nextIdNumber == null && (list == null || list.isEmpty())) {
            System.out.print("Enter First ERP ID: ");
            if (!scanner.hasNextLine()) return;
            rawErp = scanner.nextLine().trim();
            while (rawErp.isEmpty() && scanner.hasNextLine()) {
                rawErp = scanner.nextLine().trim();
            }
            if (parseErpInput(rawErp) == null) {
                System.out.println("Invalid ERP ID. Please enter a valid ERP number.");
                return;
            }
        }

        System.out.print("Enter Name: ");
        if (!scanner.hasNextLine()) return;
        String name = scanner.nextLine().trim();
        while (name.isEmpty() && scanner.hasNextLine()) {
            name = scanner.nextLine().trim();
        }
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
                int[] marks = new int[3];
                int idx = 0;
                for (int i = 0; i < tokens.length; i++) {
                    if (idx < 3) marks[idx++] = Integer.parseInt(tokens[i]);
                }
                while (idx < 3 && scanner.hasNextLine()) {
                    String line = scanner.nextLine().trim();
                    if (line.isEmpty()) continue;
                    String[] lineTokens = line.split("[,\\s]+");
                    for (int i = 0; i < lineTokens.length; i++) {
                        if (idx < 3) marks[idx++] = Integer.parseInt(lineTokens[i]);
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

        OperationResult outcome = createAndAddResult(list, rawErp, name, m1, m2, m3);
        System.out.println(outcome.getMessage());
    }

    /**
     * Builds the formatted Display All text.
     */
    public static String formatDisplayAll(ArrayList<StudentResult> list) {
        if (list == null || list.isEmpty()) {
            return "No results found.";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append("\n");
            StudentResult r = list.get(i);
            sb.append(i + 1).append(". ").append(r.toFullDetailString());
        }
        return sb.toString();
    }

    /**
     * Feature 2: Display All
     */
    public static void displayAll(ArrayList<StudentResult> list) {
        System.out.println(formatDisplayAll(list));
    }

    /**
     * Handles Search UI interaction in Console mode.
     */
    public static void handleSearch(Scanner scanner, ArrayList<StudentResult> list) {
        System.out.print("Enter ERP ID to search: ");
        if (!scanner.hasNextLine()) return;
        String searchId = scanner.nextLine().trim();
        while (searchId.isEmpty() && scanner.hasNextLine()) {
            searchId = scanner.nextLine().trim();
        }

        StudentResult found = searchById(list, searchId);
        if (found != null) {
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
        if (list == null || searchId == null || searchId.trim().isEmpty()) {
            return null;
        }
        String targetKey = normalizeIdKey(searchId);
        if (targetKey.isEmpty() || "ERP".equals(targetKey)) {
            return null;
        }
        for (int i = 0; i < list.size(); i++) {
            StudentResult current = list.get(i);
            if (normalizeIdKey(current.getId()).equals(targetKey)) {
                return current;
            }
        }
        return null;
    }

    /**
     * Manual Linear Search returning the index in the ArrayList, or -1 if not found.
     */
    public static int findIndexById(ArrayList<StudentResult> list, String searchId) {
        if (list == null || searchId == null || searchId.trim().isEmpty()) {
            return -1;
        }
        String targetKey = normalizeIdKey(searchId);
        if (targetKey.isEmpty() || "ERP".equals(targetKey)) {
            return -1;
        }
        for (int i = 0; i < list.size(); i++) {
            if (normalizeIdKey(list.get(i).getId()).equals(targetKey)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Builds the formatted Sort output:
     * 1. ERP 501 | Kiran Patel | 80.00 | Pass
     * 2. ERP 502 | Mitali Roy | 34.33 | Fail
     */
    public static String formatSortedResults(ArrayList<StudentResult> list) {
        if (list == null || list.isEmpty()) {
            return "No results found.";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append("\n");
            StudentResult r = list.get(i);
            sb.append(i + 1).append(". ").append(r.toSortString());
        }
        return sb.toString();
    }

    /**
     * Handles Sort UI interaction and displays results sorted high to low by percentage.
     */
    public static void handleSort(ArrayList<StudentResult> list) {
        if (list.isEmpty()) {
            System.out.println("No results found.");
            return;
        }

        sortByPercentage(list);
        System.out.println(formatSortedResults(list));
    }

    /**
     * Feature 4: Sort by Percentage (high to low)
     * Manual Bubble Sort Algorithm (no built-in sort methods).
     * Time Complexity: O(N^2), Best Case: O(N), Space Complexity: O(1).
     */
    public static void sortByPercentage(ArrayList<StudentResult> list) {
        if (list == null || list.size() <= 1) {
            return;
        }
        int n = list.size();
        for (int i = 0; i < n - 1; i++) {
            boolean swapped = false;
            for (int j = 0; j < n - i - 1; j++) {
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
     * Normalizes loaded IDs to "ERP <num>" format.
     */
    private static String normalizeLoadedId(String rawId) {
        if (rawId == null) return "ERP";
        String parsed = parseErpInput(rawId);
        return (parsed != null) ? parsed : rawId.trim();
    }

    /**
     * Parses a single line from results.txt into a StudentResult object.
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
                    String id = normalizeLoadedId(parts[0]);
                    String name = parts[1];
                    int total = 0;
                    double percentage = 0.0;
                    String result = "Pass";
                    for (int i = 0; i < parts.length; i++) {
                        String p = parts[i];
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
                    String id = normalizeLoadedId(parts[0]);
                    String name = parts[1];
                    double percentage = Double.parseDouble(parts[2]);
                    String result = parts[3];
                    int total = (int) Math.round((percentage * 300.0) / 100.0);
                    int m = total / 3;
                    int rem = total % 3;
                    return new StudentResult(id, name, m + (rem > 0 ? 1 : 0), m + (rem > 1 ? 1 : 0), m, total, percentage, result);
                }

                if (parts.length == 5) {
                    String id = normalizeLoadedId(parts[0]);
                    String name = parts[1];
                    int m1 = Integer.parseInt(parts[2]);
                    int m2 = Integer.parseInt(parts[3]);
                    int m3 = Integer.parseInt(parts[4]);
                    return new StudentResult(id, name, m1, m2, m3);
                }

                if (parts.length >= 8) {
                    String id = normalizeLoadedId(parts[0]);
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
                    String id = normalizeLoadedId(parts[0]);
                    String name = parts[1];
                    int m1 = Integer.parseInt(parts[2]);
                    int m2 = Integer.parseInt(parts[3]);
                    int m3 = Integer.parseInt(parts[4]);
                    return new StudentResult(id, name, m1, m2, m3);
                } else if (parts.length >= 8) {
                    String id = normalizeLoadedId(parts[0]);
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
        }

        return null;
    }

    /**
     * Feature 5: File Handling - Save data to file on exit.
     */
    public static void saveResultsToFile(ArrayList<StudentResult> list, String fileName) {
        if (list == null || fileName == null) return;
        File file = new File(fileName);
        if (list.isEmpty()) {
            if (file.exists()) {
                file.delete();
            }
            return;
        }
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            for (int i = 0; i < list.size(); i++) {
                writer.write(list.get(i).toFileString());
                writer.newLine();
            }
        } catch (IOException e) {
            System.err.println("Error saving to " + fileName + ": " + e.getMessage());
        }
    }

    /**
     * Clears all records in memory, deletes/clears the saved file, and resets nextIdNumber to null ("ERP").
     */
    public static void clearAllResults(ArrayList<StudentResult> list, String fileName) {
        if (list != null) {
            list.clear();
        }
        updateNextIdNumber(list);
        if (fileName != null) {
            File file = new File(fileName);
            if (file.exists() && !file.delete()) {
                try (FileWriter fw = new FileWriter(file, false)) {
                    fw.write("");
                } catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * Auto-detects the maximum ERP ID sequence number and sets nextIdNumber = max + 1.
     * When 0 records exist, nextIdNumber is null so the UI displays "ERP" with no hardcoded number.
     */
    public static void updateNextIdNumber(ArrayList<StudentResult> list) {
        if (list == null || list.isEmpty()) {
            nextIdNumber = null;
            return;
        }
        Integer maxId = null;
        for (int i = 0; i < list.size(); i++) {
            String id = list.get(i).getId();
            if (id != null) {
                String digits = id.replaceAll("[^0-9]", "");
                if (!digits.isEmpty()) {
                    try {
                        int num = Integer.parseInt(digits);
                        if (maxId == null || num > maxId) {
                            maxId = num;
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        nextIdNumber = (maxId == null) ? null : (maxId + 1);
    }

    public static Integer getNextIdNumber() {
        return nextIdNumber;
    }

    public static void setNextIdNumber(Integer val) {
        nextIdNumber = val;
    }
}

/**
 * Custom anti-aliased rounded card panel for smooth desktop rendering.
 */
class RoundedCardPanel extends JPanel {
    private final Color fillColor;
    private final Color strokeColor;
    private final int radius;

    public RoundedCardPanel(Color fillColor, Color strokeColor, int radius) {
        this.fillColor = fillColor;
        this.strokeColor = strokeColor;
        this.radius = radius;
        setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        g2.setColor(fillColor);
        g2.fillRoundRect(0, 0, w - 1, h - 1, radius, radius);
        g2.setColor(strokeColor);
        g2.drawRoundRect(0, 0, w - 1, h - 1, radius, radius);
        g2.dispose();
        super.paintComponent(g);
    }
}

/**
 * Custom anti-aliased smooth hover button.
 */
class SmoothButton extends JButton {
    private final Color baseBg;
    private final Color hoverBg;
    private boolean hovered = false;

    public SmoothButton(String text, Color baseBg, Color fg) {
        super(text);
        this.baseBg = baseBg;
        this.hoverBg = brighten(baseBg, 0.10f);
        setForeground(fg);
        setFont(new Font("SansSerif", Font.BOLD, 12));
        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setBorder(new EmptyBorder(9, 15, 9, 15));

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hovered = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hovered = false;
                repaint();
            }
        });
    }

    private static Color brighten(Color c, float factor) {
        int r = Math.min(255, Math.round(c.getRed() + (255 - c.getRed()) * factor));
        int g = Math.min(255, Math.round(c.getGreen() + (255 - c.getGreen()) * factor));
        int b = Math.min(255, Math.round(c.getBlue() + (255 - c.getBlue()) * factor));
        return new Color(r, g, b);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(getModel().isPressed() ? baseBg.darker() : (hovered ? hoverBg : baseBg));
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
        g2.dispose();
        super.paintComponent(g);
    }
}

/**
 * Smooth, modernized Java Swing Desktop GUI for Exam Result Management System.
 */
class ExamResultGUI extends JFrame {
    private static final Color BG_APP = new Color(241, 245, 249);
    private static final Color BG_CARD = Color.WHITE;
    private static final Color HEADER_BG = new Color(15, 23, 42);
    private static final Color PRIMARY = new Color(37, 99, 235);
    private static final Color INDIGO = new Color(79, 70, 229);
    private static final Color TEAL = new Color(13, 148, 136);
    private static final Color SLATE_BTN = new Color(51, 65, 85);
    private static final Color DANGER_BTN = new Color(185, 28, 28);
    private static final Color BORDER_COLOR = new Color(226, 232, 240);
    private static final Color TEXT_MAIN = new Color(15, 23, 42);
    private static final Color TEXT_MUTED = new Color(100, 116, 139);
    private static final Color PASS_BG = new Color(220, 252, 231);
    private static final Color PASS_FG = new Color(22, 101, 52);
    private static final Color FAIL_BG = new Color(254, 226, 226);
    private static final Color FAIL_FG = new Color(153, 27, 27);
    private static final Color BANNER_OK_BG = new Color(236, 253, 245);
    private static final Color BANNER_OK_FG = new Color(6, 95, 70);
    private static final Color BANNER_ERR_BG = new Color(254, 242, 242);
    private static final Color BANNER_ERR_FG = new Color(153, 27, 27);
    private static final Color BANNER_INFO_BG = new Color(239, 246, 255);
    private static final Color BANNER_INFO_FG = new Color(30, 64, 175);

    private final ArrayList<StudentResult> resultsList;
    private final String fileName;

    private JLabel nextIdStatLabel;
    private JLabel totalRecordsStatLabel;
    private JLabel passCountStatLabel;
    private JLabel failCountStatLabel;

    private JLabel formNextIdBadge;
    private JTextField erpIdField;
    private JTextField nameField;
    private JTextField mark1Field;
    private JTextField mark2Field;
    private JTextField mark3Field;
    private JTextField searchIdField;

    private JPanel statusBannerPanel;
    private JLabel statusBannerLabel;
    private JLabel tableModeLabel;
    private DefaultTableModel tableModel;
    private JTable resultsTable;
    private JTextArea outputLogArea;

    public ExamResultGUI(ArrayList<StudentResult> resultsList, String fileName) {
        super("Exam Result Management System");
        this.resultsList = resultsList;
        this.fileName = fileName;

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(1060, 720));
        setSize(1140, 780);
        setLocationRelativeTo(null);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                saveAndExit();
            }
        });

        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(BG_APP);
        root.add(buildHeaderPanel(), BorderLayout.NORTH);
        root.add(buildMainBodyPanel(), BorderLayout.CENTER);
        setContentPane(root);

        refreshTable(false);
        updateStats();
        String nextErp = ExamResultManagementSystem.formatNextId();
        if (resultsList.isEmpty()) {
            setBanner("Ready. Enter the first student's ERP ID to begin — subsequent IDs auto-detect automatically.", false, true);
            appendLog("Loaded " + fileName + ": 0 existing records. Enter first ERP ID to start auto-detection.");
        } else {
            setBanner("Loaded " + resultsList.size() + " record(s) from " + fileName + ". Auto-detected next ID: " + nextErp, true, false);
            appendLog("Loaded " + resultsList.size() + " record(s) from " + fileName + ":");
            appendLog(ExamResultManagementSystem.formatDisplayAll(resultsList));
        }
    }

    private JPanel buildHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout(20, 0));
        header.setBackground(HEADER_BG);
        header.setBorder(new EmptyBorder(16, 24, 16, 24));

        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Exam Result Management System");
        title.setFont(new Font("SansSerif", Font.BOLD, 21));
        title.setForeground(Color.WHITE);

        JLabel subtitle = new JLabel("PS No. 48  •  File: " + fileName + "  •  3 Subjects (/100 each)  •  Pass >= 40.00%");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 12));
        subtitle.setForeground(new Color(148, 163, 184));

        titleBox.add(title);
        titleBox.add(Box.createVerticalStrut(4));
        titleBox.add(subtitle);

        JPanel statsPanel = new JPanel(new GridLayout(1, 4, 10, 0));
        statsPanel.setOpaque(false);

        nextIdStatLabel = new JLabel("ERP", SwingConstants.CENTER);
        totalRecordsStatLabel = new JLabel("0", SwingConstants.CENTER);
        passCountStatLabel = new JLabel("0", SwingConstants.CENTER);
        failCountStatLabel = new JLabel("0", SwingConstants.CENTER);

        statsPanel.add(createHeaderStatCard("NEXT ERP", nextIdStatLabel, new Color(96, 165, 250)));
        statsPanel.add(createHeaderStatCard("RECORDS", totalRecordsStatLabel, Color.WHITE));
        statsPanel.add(createHeaderStatCard("PASS", passCountStatLabel, new Color(74, 222, 128)));
        statsPanel.add(createHeaderStatCard("FAIL", failCountStatLabel, new Color(248, 113, 113)));

        header.add(titleBox, BorderLayout.WEST);
        header.add(statsPanel, BorderLayout.EAST);
        return header;
    }

    private JPanel createHeaderStatCard(String caption, JLabel valueLabel, Color valueColor) {
        RoundedCardPanel card = new RoundedCardPanel(new Color(30, 41, 59), new Color(51, 65, 85), 12);
        card.setLayout(new BorderLayout(0, 2));
        card.setBorder(new EmptyBorder(6, 14, 6, 14));

        JLabel cap = new JLabel(caption, SwingConstants.CENTER);
        cap.setFont(new Font("SansSerif", Font.BOLD, 10));
        cap.setForeground(new Color(148, 163, 184));

        valueLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
        valueLabel.setForeground(valueColor);

        card.add(cap, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildMainBodyPanel() {
        JPanel body = new JPanel(new BorderLayout(16, 12));
        body.setBackground(BG_APP);
        body.setBorder(new EmptyBorder(14, 18, 16, 18));

        statusBannerPanel = new JPanel(new BorderLayout());
        statusBannerPanel.setBackground(BANNER_INFO_BG);
        statusBannerPanel.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(191, 219, 254), 1, true),
                new EmptyBorder(10, 14, 10, 14)));

        statusBannerLabel = new JLabel("Ready.");
        statusBannerLabel.setFont(new Font("Monospaced", Font.BOLD, 13));
        statusBannerLabel.setForeground(BANNER_INFO_FG);
        statusBannerPanel.add(statusBannerLabel, BorderLayout.CENTER);

        body.add(statusBannerPanel, BorderLayout.NORTH);

        JPanel leftSidebar = buildLeftSidebar();
        JSplitPane rightSplit = buildRightSplitPane();

        JPanel centerContainer = new JPanel(new BorderLayout(16, 0));
        centerContainer.setOpaque(false);
        centerContainer.add(leftSidebar, BorderLayout.WEST);
        centerContainer.add(rightSplit, BorderLayout.CENTER);

        body.add(centerContainer, BorderLayout.CENTER);
        return body;
    }

    private JPanel buildLeftSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setOpaque(false);
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setPreferredSize(new Dimension(336, 540));

        sidebar.add(buildAddResultCard());
        sidebar.add(Box.createVerticalStrut(12));
        sidebar.add(buildSearchCard());
        sidebar.add(Box.createVerticalStrut(12));
        sidebar.add(buildMenuOperationsCard());
        sidebar.add(Box.createVerticalGlue());

        return sidebar;
    }

    private JPanel buildAddResultCard() {
        JPanel card = createCardPanel();
        card.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);
        JLabel sectionTitle = new JLabel("1. Add Result");
        sectionTitle.setFont(new Font("SansSerif", Font.BOLD, 15));
        sectionTitle.setForeground(TEXT_MAIN);

        formNextIdBadge = new JLabel("ERP");
        formNextIdBadge.setFont(new Font("Monospaced", Font.BOLD, 12));
        formNextIdBadge.setForeground(PRIMARY);
        formNextIdBadge.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(191, 219, 254), 1, true),
                new EmptyBorder(3, 9, 3, 9)));
        formNextIdBadge.setOpaque(true);
        formNextIdBadge.setBackground(new Color(239, 246, 255));

        topRow.add(sectionTitle, BorderLayout.WEST);
        topRow.add(formNextIdBadge, BorderLayout.EAST);

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 3;
        card.add(topRow, gbc);

        gbc.gridy = 1;
        gbc.insets = new Insets(8, 4, 2, 4);
        card.add(createFieldLabel("ERP ID"), gbc);

        erpIdField = createStyledTextField("");
        erpIdField.setToolTipText("Enter first ERP ID; subsequent ERP IDs auto-detect automatically");
        gbc.gridy = 2;
        gbc.insets = new Insets(0, 4, 6, 4);
        card.add(erpIdField, gbc);

        gbc.gridy = 3;
        gbc.insets = new Insets(4, 4, 2, 4);
        card.add(createFieldLabel("Student Name"), gbc);

        nameField = createStyledTextField("");
        nameField.setToolTipText("e.g. Kiran Patel");
        gbc.gridy = 4;
        gbc.insets = new Insets(0, 4, 6, 4);
        card.add(nameField, gbc);

        gbc.gridy = 5;
        gbc.insets = new Insets(4, 4, 2, 4);
        card.add(createFieldLabel("Marks in 3 Subjects (0 - 100 each)"), gbc);

        mark1Field = createStyledTextField("");
        mark2Field = createStyledTextField("");
        mark3Field = createStyledTextField("");
        mark1Field.setHorizontalAlignment(JTextField.CENTER);
        mark2Field.setHorizontalAlignment(JTextField.CENTER);
        mark3Field.setHorizontalAlignment(JTextField.CENTER);

        JPanel marksGrid = new JPanel(new GridLayout(2, 3, 8, 2));
        marksGrid.setOpaque(false);
        marksGrid.add(createSubCaption("Subject 1"));
        marksGrid.add(createSubCaption("Subject 2"));
        marksGrid.add(createSubCaption("Subject 3"));
        marksGrid.add(mark1Field);
        marksGrid.add(mark2Field);
        marksGrid.add(mark3Field);

        gbc.gridy = 6;
        gbc.insets = new Insets(0, 4, 8, 4);
        card.add(marksGrid, gbc);

        JPanel btnRow = new JPanel(new BorderLayout(8, 0));
        btnRow.setOpaque(false);

        JButton addBtn = createStyledButton("1 - Add Result", PRIMARY, Color.WHITE);
        JButton clearBtn = createStyledButton("Clear", new Color(226, 232, 240), TEXT_MAIN);

        ActionListener submitAddAction = e -> onAddResult();
        addBtn.addActionListener(submitAddAction);
        mark1Field.addActionListener(submitAddAction);
        mark2Field.addActionListener(submitAddAction);
        mark3Field.addActionListener(submitAddAction);

        clearBtn.addActionListener(e -> {
            String autoErp = ExamResultManagementSystem.formatNextId();
            erpIdField.setText("ERP".equals(autoErp) ? "" : autoErp);
            nameField.setText("");
            mark1Field.setText("");
            mark2Field.setText("");
            mark3Field.setText("");
            if ("ERP".equals(autoErp)) {
                erpIdField.requestFocusInWindow();
            } else {
                nameField.requestFocusInWindow();
            }
        });

        btnRow.add(addBtn, BorderLayout.CENTER);
        btnRow.add(clearBtn, BorderLayout.EAST);

        gbc.gridy = 7;
        gbc.insets = new Insets(2, 4, 2, 4);
        card.add(btnRow, gbc);

        return card;
    }

    private JPanel buildSearchCard() {
        JPanel card = createCardPanel();
        card.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.gridx = 0;

        // Clean title without "(Linear Search)"
        JLabel title = new JLabel("3. Search by ID");
        title.setFont(new Font("SansSerif", Font.BOLD, 15));
        title.setForeground(TEXT_MAIN);
        gbc.gridy = 0;
        gbc.insets = new Insets(2, 4, 6, 4);
        card.add(title, gbc);

        gbc.gridy = 1;
        gbc.insets = new Insets(0, 4, 2, 4);
        card.add(createFieldLabel("Enter ERP ID"), gbc);

        JPanel searchRow = new JPanel(new BorderLayout(8, 0));
        searchRow.setOpaque(false);
        searchIdField = createStyledTextField("");
        JButton searchBtn = createStyledButton("3 - Search", TEAL, Color.WHITE);

        ActionListener searchAction = e -> onSearchById();
        searchBtn.addActionListener(searchAction);
        searchIdField.addActionListener(searchAction);

        searchRow.add(searchIdField, BorderLayout.CENTER);
        searchRow.add(searchBtn, BorderLayout.EAST);

        gbc.gridy = 2;
        gbc.insets = new Insets(0, 4, 2, 4);
        card.add(searchRow, gbc);

        return card;
    }

    private JPanel buildMenuOperationsCard() {
        JPanel card = createCardPanel();
        card.setLayout(new GridLayout(0, 1, 0, 7));

        JLabel title = new JLabel("Menu Operations & Persistence");
        title.setFont(new Font("SansSerif", Font.BOLD, 15));
        title.setForeground(TEXT_MAIN);

        JButton displayAllBtn = createStyledButton("2 - Display All Results", SLATE_BTN, Color.WHITE);
        JButton sortBtn = createStyledButton("4 - Sort by Percentage (High to Low)", INDIGO, Color.WHITE);
        JButton saveBtn = createStyledButton("Save Now to " + fileName, new Color(22, 163, 74), Color.WHITE);
        JButton clearAllBtn = createStyledButton("Clear All Saved Records", new Color(217, 119, 6), Color.WHITE);
        JButton exitBtn = createStyledButton("5 - Save & Exit", DANGER_BTN, Color.WHITE);

        displayAllBtn.addActionListener(e -> onDisplayAll());
        sortBtn.addActionListener(e -> onSortByPercentage());
        saveBtn.addActionListener(e -> onSaveNow());
        clearAllBtn.addActionListener(e -> onClearAllRecords());
        exitBtn.addActionListener(e -> saveAndExit());

        card.add(title);
        card.add(displayAllBtn);
        card.add(sortBtn);
        card.add(saveBtn);
        card.add(clearAllBtn);
        card.add(exitBtn);

        return card;
    }

    private JSplitPane buildRightSplitPane() {
        JPanel tableCard = createCardPanel();
        tableCard.setLayout(new BorderLayout(0, 8));

        JPanel tableHeaderBar = new JPanel(new BorderLayout());
        tableHeaderBar.setOpaque(false);

        // Clean title without "(ArrayList<StudentResult>)"
        JLabel tableTitle = new JLabel("Student Exam Results");
        tableTitle.setFont(new Font("SansSerif", Font.BOLD, 15));
        tableTitle.setForeground(TEXT_MAIN);

        tableModeLabel = new JLabel("Showing all records");
        tableModeLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        tableModeLabel.setForeground(TEXT_MUTED);

        tableHeaderBar.add(tableTitle, BorderLayout.WEST);
        tableHeaderBar.add(tableModeLabel, BorderLayout.EAST);

        String[] columns = {
            "#", "ERP ID", "Student Name", "Sub 1", "Sub 2", "Sub 3", "Total (/300)", "Percentage", "Result"
        };
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        resultsTable = new JTable(tableModel);
        resultsTable.setAutoCreateRowSorter(false);
        resultsTable.setRowHeight(32);
        resultsTable.setFont(new Font("SansSerif", Font.PLAIN, 13));
        resultsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultsTable.setGridColor(new Color(241, 245, 249));
        resultsTable.setSelectionBackground(new Color(219, 234, 254));
        resultsTable.setSelectionForeground(TEXT_MAIN);
        resultsTable.setFillsViewportHeight(true);

        JTableHeader th = resultsTable.getTableHeader();
        th.setReorderingAllowed(false);
        th.setFont(new Font("SansSerif", Font.BOLD, 12));
        th.setBackground(new Color(241, 245, 249));
        th.setForeground(TEXT_MAIN);
        th.setPreferredSize(new Dimension(th.getPreferredSize().width, 36));

        int[] widths = {38, 96, 165, 58, 58, 58, 88, 90, 76};
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);

        for (int i = 0; i < widths.length; i++) {
            resultsTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
            if (i != 2) {
                resultsTable.getColumnModel().getColumn(i).setCellRenderer(centerRenderer);
            }
        }

        resultsTable.getColumnModel().getColumn(8).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel lbl = (JLabel) super.getTableCellRendererComponent(
                        table, value, isSelected, hasFocus, row, column);
                lbl.setHorizontalAlignment(SwingConstants.CENTER);
                lbl.setFont(new Font("SansSerif", Font.BOLD, 12));
                String text = (value == null) ? "" : value.toString();
                if (!isSelected) {
                    if ("Pass".equalsIgnoreCase(text)) {
                        lbl.setBackground(PASS_BG);
                        lbl.setForeground(PASS_FG);
                    } else {
                        lbl.setBackground(FAIL_BG);
                        lbl.setForeground(FAIL_FG);
                    }
                }
                return lbl;
            }
        });

        JScrollPane tableScroll = new JScrollPane(resultsTable);
        tableScroll.setBorder(new LineBorder(BORDER_COLOR, 1));

        tableCard.add(tableHeaderBar, BorderLayout.NORTH);
        tableCard.add(tableScroll, BorderLayout.CENTER);

        JPanel logCard = createCardPanel();
        logCard.setLayout(new BorderLayout(0, 6));

        JPanel logHeader = new JPanel(new BorderLayout());
        logHeader.setOpaque(false);
        JLabel logTitle = new JLabel("System Output Console");
        logTitle.setFont(new Font("SansSerif", Font.BOLD, 13));
        logTitle.setForeground(TEXT_MAIN);

        JButton clearLogBtn = createStyledButton("Clear Log", new Color(226, 232, 240), TEXT_MUTED);
        clearLogBtn.setFont(new Font("SansSerif", Font.PLAIN, 11));
        clearLogBtn.setBorder(new EmptyBorder(5, 10, 5, 10));
        clearLogBtn.addActionListener(e -> outputLogArea.setText(""));

        logHeader.add(logTitle, BorderLayout.WEST);
        logHeader.add(clearLogBtn, BorderLayout.EAST);

        outputLogArea = new JTextArea(6, 40);
        outputLogArea.setEditable(false);
        outputLogArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        outputLogArea.setBackground(new Color(15, 23, 42));
        outputLogArea.setForeground(new Color(226, 232, 240));
        outputLogArea.setBorder(new EmptyBorder(10, 12, 10, 12));
        outputLogArea.setLineWrap(false);

        JScrollPane logScroll = new JScrollPane(outputLogArea);
        logScroll.setBorder(new LineBorder(new Color(30, 41, 59), 1));

        logCard.add(logHeader, BorderLayout.NORTH);
        logCard.add(logScroll, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableCard, logCard);
        split.setResizeWeight(0.68);
        split.setBorder(null);
        split.setOpaque(false);
        split.setDividerSize(8);
        return split;
    }

    private void onAddResult() {
        String rawErp = erpIdField.getText();
        String rawName = nameField.getText();
        String s1 = mark1Field.getText();
        String s2 = mark2Field.getText();
        String s3 = mark3Field.getText();

        OperationResult outcome = ExamResultManagementSystem.addResultFromInputs(
                resultsList, rawErp, rawName, s1, s2, s3);
        appendLog("> Menu Option 1 (Add Result)");
        appendLog(outcome.getMessage());

        if (outcome.isSuccess()) {
            ExamResultManagementSystem.saveResultsToFile(resultsList, fileName);
            refreshTable(false);
            updateStats();
            setBanner(outcome.getMessage(), true, false);

            int lastRow = resultsList.size() - 1;
            if (lastRow >= 0) {
                resultsTable.setRowSelectionInterval(lastRow, lastRow);
                resultsTable.scrollRectToVisible(resultsTable.getCellRect(lastRow, 0, true));
            }

            nameField.setText("");
            mark1Field.setText("");
            mark2Field.setText("");
            mark3Field.setText("");
            nameField.requestFocusInWindow();
        } else {
            setBanner(outcome.getMessage(), false, false);
            JOptionPane.showMessageDialog(
                    this,
                    outcome.getMessage(),
                    "Invalid Input",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onDisplayAll() {
        refreshTable(false);
        tableModeLabel.setText("2 - Display All (" + resultsList.size() + " record(s))");
        String output = ExamResultManagementSystem.formatDisplayAll(resultsList);
        appendLog("> Menu Option 2 (Display All)");
        appendLog(output);

        if (resultsList.isEmpty()) {
            setBanner("No results found.", false, true);
        } else {
            setBanner("Displaying all " + resultsList.size() + " student result(s).", false, true);
        }
    }

    private void onSearchById() {
        String query = searchIdField.getText().trim();
        if (query.isEmpty()) {
            String msg = "Please enter an ERP ID to search.";
            setBanner(msg, false, false);
            return;
        }

        appendLog("> Menu Option 3 (Search by ID: " + query + ")");
        StudentResult found = ExamResultManagementSystem.searchById(resultsList, query);
        if (found != null) {
            String exactMsg = "Found: " + found.toFullDetailString();
            appendLog(exactMsg);
            setBanner(exactMsg, true, false);
            refreshTable(false);

            int idx = ExamResultManagementSystem.findIndexById(resultsList, query);
            if (idx >= 0) {
                resultsTable.setRowSelectionInterval(idx, idx);
                resultsTable.scrollRectToVisible(resultsTable.getCellRect(idx, 0, true));
            }
            tableModeLabel.setText("Search matched row #" + (idx + 1) + " (" + found.getId() + ")");
        } else {
            String notFoundMsg = "Result not found for ID: " + query;
            appendLog(notFoundMsg);
            setBanner(notFoundMsg, false, false);
            resultsTable.clearSelection();
        }
    }

    private void onSortByPercentage() {
        appendLog("> Menu Option 4 (Sort by Percentage - high to low)");
        if (resultsList.isEmpty()) {
            appendLog("No results found.");
            setBanner("No results found.", false, true);
            return;
        }

        ExamResultManagementSystem.sortByPercentage(resultsList);
        ExamResultManagementSystem.saveResultsToFile(resultsList, fileName);

        refreshTable(true);
        String sortedText = ExamResultManagementSystem.formatSortedResults(resultsList);
        appendLog(sortedText);

        StudentResult top = resultsList.get(0);
        setBanner("Sorted by Percentage (high to low). Top: " + top.toSortString(), true, false);
    }

    private void onSaveNow() {
        ExamResultManagementSystem.saveResultsToFile(resultsList, fileName);
        String msg = "Saved " + resultsList.size() + " record(s) to " + fileName + ".";
        appendLog("> " + msg);
        setBanner(msg, true, false);
    }

    private void onClearAllRecords() {
        if (resultsList.isEmpty() && !(new File(fileName).exists())
                && ExamResultManagementSystem.getNextIdNumber() == null) {
            String msg = "No saved records to clear. Auto-detected ERP ID is already ERP.";
            appendLog("> " + msg);
            setBanner(msg, false, true);
            return;
        }

        int choice = JOptionPane.showConfirmDialog(
                this,
                "Delete all " + resultsList.size() + " saved record(s) from " + fileName
                        + " and reset the auto-detected ERP ID back to ERP?",
                "Confirm Clear All Records",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (choice == JOptionPane.YES_OPTION) {
            ExamResultManagementSystem.clearAllResults(resultsList, fileName);
            searchIdField.setText("");
            refreshTable(false);
            updateStats();
            tableModeLabel.setText("Showing 0 record(s)");
            String msg = "Cleared all saved records from " + fileName + ". Auto-detected ERP ID reset to ERP.";
            appendLog("> " + msg);
            setBanner(msg, true, false);
            erpIdField.requestFocusInWindow();
        }
    }

    private void saveAndExit() {
        ExamResultManagementSystem.saveResultsToFile(resultsList, fileName);
        dispose();
        System.exit(0);
    }

    private void refreshTable(boolean sortedView) {
        tableModel.setRowCount(0);
        for (int i = 0; i < resultsList.size(); i++) {
            StudentResult r = resultsList.get(i);
            tableModel.addRow(new Object[]{
                i + 1,
                r.getId(),
                r.getName(),
                r.getMark1(),
                r.getMark2(),
                r.getMark3(),
                r.getTotal() + "/300",
                String.format(Locale.US, "%.2f", r.getPercentage()),
                r.getResult()
            });
        }
        if (sortedView) {
            tableModeLabel.setText("Sorted by Percentage: High to Low");
        } else {
            tableModeLabel.setText("Showing " + resultsList.size() + " record(s)");
        }
    }

    private void updateStats() {
        String nextId = ExamResultManagementSystem.formatNextId();
        nextIdStatLabel.setText(nextId);
        formNextIdBadge.setText(nextId);
        if (erpIdField != null) {
            erpIdField.setText("ERP".equals(nextId) ? "" : nextId);
        }
        totalRecordsStatLabel.setText(String.valueOf(resultsList.size()));

        int passCount = 0;
        int failCount = 0;
        for (int i = 0; i < resultsList.size(); i++) {
            if ("Pass".equalsIgnoreCase(resultsList.get(i).getResult())) {
                passCount++;
            } else {
                failCount++;
            }
        }
        passCountStatLabel.setText(String.valueOf(passCount));
        failCountStatLabel.setText(String.valueOf(failCount));
    }

    private void setBanner(String text, boolean isSuccess, boolean isNeutralInfo) {
        statusBannerLabel.setText(text);
        if (isNeutralInfo) {
            statusBannerPanel.setBackground(BANNER_INFO_BG);
            statusBannerPanel.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(new Color(191, 219, 254), 1, true),
                    new EmptyBorder(10, 14, 10, 14)));
            statusBannerLabel.setForeground(BANNER_INFO_FG);
        } else if (isSuccess) {
            statusBannerPanel.setBackground(BANNER_OK_BG);
            statusBannerPanel.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(new Color(167, 243, 208), 1, true),
                    new EmptyBorder(10, 14, 10, 14)));
            statusBannerLabel.setForeground(BANNER_OK_FG);
        } else {
            statusBannerPanel.setBackground(BANNER_ERR_BG);
            statusBannerPanel.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(new Color(254, 202, 202), 1, true),
                    new EmptyBorder(10, 14, 10, 14)));
            statusBannerLabel.setForeground(BANNER_ERR_FG);
        }
    }

    private void appendLog(String text) {
        if (outputLogArea.getText().isEmpty()) {
            outputLogArea.append(text);
        } else {
            outputLogArea.append("\n" + text);
        }
        outputLogArea.setCaretPosition(outputLogArea.getDocument().getLength());
    }

    private JPanel createCardPanel() {
        RoundedCardPanel card = new RoundedCardPanel(BG_CARD, BORDER_COLOR, 16);
        card.setBorder(new EmptyBorder(13, 15, 13, 15));
        return card;
    }

    private JLabel createFieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", Font.BOLD, 12));
        label.setForeground(TEXT_MUTED);
        return label;
    }

    private JLabel createSubCaption(String text) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setFont(new Font("SansSerif", Font.PLAIN, 11));
        label.setForeground(TEXT_MUTED);
        return label;
    }

    private JTextField createStyledTextField(String placeholder) {
        JTextField tf = new JTextField(placeholder);
        tf.setFont(new Font("SansSerif", Font.PLAIN, 13));
        tf.setForeground(TEXT_MAIN);
        tf.setBackground(new Color(248, 250, 252));
        tf.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(203, 213, 225), 1, true),
                new EmptyBorder(7, 10, 7, 10)));
        return tf;
    }

    private JButton createStyledButton(String text, Color bg, Color fg) {
        return new SmoothButton(text, bg, fg);
    }
}
