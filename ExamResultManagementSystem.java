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
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
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
    private String[] subjectNames;
    private int[] marks;
    private int total;
    private int maxMarks;
    private double percentage;
    private String result;

    public StudentResult(String id, String name, int... marks) {
        this(id, name, buildDefaultSubjectNames(marks == null ? 0 : marks.length), marks);
    }

    public StudentResult(String id, String name, String[] subjectNames, int[] marks) {
        this.id = id;
        this.name = name;
        this.marks = (marks == null) ? new int[0] : marks.clone();
        this.subjectNames = new String[this.marks.length];
        for (int i = 0; i < this.marks.length; i++) {
            String sn = (subjectNames != null && i < subjectNames.length && subjectNames[i] != null
                    && !subjectNames[i].trim().isEmpty())
                    ? subjectNames[i].trim()
                    : ("Subject " + (i + 1));
            this.subjectNames[i] = sn;
        }
        int sum = 0;
        for (int i = 0; i < this.marks.length; i++) {
            sum += this.marks[i];
        }
        this.total = sum;
        this.maxMarks = this.marks.length * 100;
        this.percentage = (this.maxMarks > 0) ? (this.total * 100.0) / this.maxMarks : 0.0;
        this.result = (this.percentage >= 40.0) ? "Pass" : "Fail";
    }

    public StudentResult(
            String id, String name, String[] subjectNames, int[] marks, int total, int maxMarks, double percentage, String result) {
        this.id = id;
        this.name = name;
        this.marks = (marks == null) ? new int[0] : marks.clone();
        this.subjectNames = new String[this.marks.length];
        for (int i = 0; i < this.marks.length; i++) {
            this.subjectNames[i] = (subjectNames != null && i < subjectNames.length && subjectNames[i] != null
                    && !subjectNames[i].trim().isEmpty())
                    ? subjectNames[i].trim()
                    : ("Subject " + (i + 1));
        }
        this.total = total;
        this.maxMarks = maxMarks;
        this.percentage = percentage;
        this.result = result;
    }

    private static String[] buildDefaultSubjectNames(int count) {
        String[] names = new String[count];
        for (int i = 0; i < count; i++) {
            names[i] = "Subject " + (i + 1);
        }
        return names;
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

    public String[] getSubjectNames() {
        return subjectNames.clone();
    }

    public String getSubjectNameAt(int index) {
        return (index >= 0 && index < subjectNames.length) ? subjectNames[index] : "";
    }

    public Integer findMarkForSubject(String targetSubject) {
        if (targetSubject == null) return null;
        String needle = targetSubject.trim();
        for (int i = 0; i < subjectNames.length; i++) {
            if (subjectNames[i].equalsIgnoreCase(needle)) {
                return marks[i];
            }
        }
        return null;
    }

    public int[] getMarks() {
        return marks.clone();
    }

    public int getSubjectCount() {
        return marks.length;
    }

    public int getMarkAt(int index) {
        return (index >= 0 && index < marks.length) ? marks[index] : -1;
    }

    public int getTotal() {
        return total;
    }

    public int getMaxMarks() {
        return maxMarks;
    }

    public double getPercentage() {
        return percentage;
    }

    public String getResult() {
        return result;
    }

    /**
     * Formats full details with dynamic maxMarks:
     * e.g.: ERP 501 | Kiran Patel | Total: 240/300 | Percentage: 80.00 | Result: Pass
     */
    public String toFullDetailString() {
        return String.format(Locale.US, "%s | %s | Total: %d/%d | Percentage: %.2f | Result: %s",
                id, name, total, maxMarks, percentage, result);
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
     * File storage format (CSV) preserving subject:mark pairs and computed metrics.
     */
    public String toFileString() {
        StringBuilder sb = new StringBuilder();
        sb.append(id).append(",").append(name);
        for (int i = 0; i < marks.length; i++) {
            String safeSub = subjectNames[i].replace(",", " ").replace(":", " ").trim();
            sb.append(",").append(safeSub).append(":").append(marks[i]);
        }
        sb.append(",").append(total)
          .append(",").append(String.format(Locale.US, "%.2f", percentage))
          .append(",").append(result);
        return sb.toString();
    }

    /**
     * Serializes this record as a JSON object for the local HTTP server and Firebase RTDB.
     */
    public String toJsonString() {
        StringBuilder subsArr = new StringBuilder("[");
        StringBuilder marksArr = new StringBuilder("[");
        for (int i = 0; i < marks.length; i++) {
            if (i > 0) {
                subsArr.append(",");
                marksArr.append(",");
            }
            subsArr.append("{\"subject\":\"")
                   .append(escapeJson(subjectNames[i]))
                   .append("\",\"mark\":")
                   .append(marks[i])
                   .append("}");
            marksArr.append(marks[i]);
        }
        subsArr.append("]");
        marksArr.append("]");
        return String.format(
                Locale.US,
                "{\"id\":\"%s\",\"name\":\"%s\",\"subjects\":%s,\"marks\":%s,\"total\":%d,\"maxMarks\":%d,\"percentage\":%.2f,\"result\":\"%s\"}",
                escapeJson(id), escapeJson(name), subsArr.toString(), marksArr.toString(), total, maxMarks, percentage, escapeJson(result));
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
                    String[][] subjectPairs = extractJsonSubjectsArray(body);
                    OperationResult op;
                    if (subjectPairs != null) {
                        op = addResultWithNamedSubjects(list, erpId, name, subjectPairs[0], subjectPairs[1]);
                    } else {
                        String[] marksArr = extractJsonStringArray(body, "marks");
                        if (marksArr != null) {
                            op = addResultFromInputs(list, erpId, name, marksArr);
                        } else {
                            String m1 = extractJsonString(body, "m1");
                            String m2 = extractJsonString(body, "m2");
                            String m3 = extractJsonString(body, "m3");
                            op = addResultFromInputs(list, erpId, name, m1, m2, m3);
                        }
                    }
                    if (op.isSuccess()) {
                        saveResultsToFile(list, FILE_NAME);
                    } else {
                        status = 400;
                    }
                    responseJson = buildStateJson(list, op.isSuccess(), op.getMessage(), op.getStudentResult(), list.size() - 1);
                } else if ("remove".equalsIgnoreCase(action)) {
                    String targetId = extractJsonString(body, "id");
                    OperationResult rem = removeResultById(list, targetId);
                    if (rem.isSuccess()) {
                        saveResultsToFile(list, FILE_NAME);
                    } else {
                        status = 404;
                    }
                    responseJson = buildStateJson(list, rem.isSuccess(), rem.getMessage(), null, -1);
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

    private static String[] extractJsonStringArray(String json, String key) {
        if (json == null || key == null) return null;
        String needle = "\"" + key + "\"";
        int kIdx = json.indexOf(needle);
        if (kIdx == -1) return null;
        int colon = json.indexOf(':', kIdx + needle.length());
        if (colon == -1) return null;
        int i = colon + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
        if (i >= json.length() || json.charAt(i) != '[') return null;

        ArrayList<String> items = new ArrayList<>();
        i++;
        while (i < json.length()) {
            while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
            if (i >= json.length() || json.charAt(i) == ']') break;
            if (json.charAt(i) == ',') {
                i++;
                continue;
            }
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
                items.add(sb.toString());
            } else {
                int start = i;
                while (i < json.length() && ",]".indexOf(json.charAt(i)) == -1) i++;
                items.add(json.substring(start, i).trim());
            }
        }
        return items.toArray(new String[0]);
    }

    private static String[][] extractJsonSubjectsArray(String json) {
        if (json == null) return null;
        String needle = "\"subjects\"";
        int kIdx = json.indexOf(needle);
        if (kIdx == -1) return null;
        int bracketStart = json.indexOf('[', kIdx + needle.length());
        if (bracketStart == -1) return null;

        ArrayList<String> subNames = new ArrayList<>();
        ArrayList<String> subMarks = new ArrayList<>();
        int i = bracketStart + 1;
        while (i < json.length()) {
            while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
            if (i >= json.length() || json.charAt(i) == ']') break;
            if (json.charAt(i) == '{') {
                int closeObj = json.indexOf('}', i);
                if (closeObj == -1) break;
                String objChunk = json.substring(i, closeObj + 1);
                subNames.add(extractJsonString(objChunk, "subject"));
                subMarks.add(extractJsonString(objChunk, "mark"));
                i = closeObj + 1;
            } else {
                i++;
            }
        }
        return new String[][]{subNames.toArray(new String[0]), subMarks.toArray(new String[0])};
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
     * sanitize name, validate dynamic subject marks (0-100 each), and append to the ArrayList.
     */
    public static OperationResult createAndAddResult(
            ArrayList<StudentResult> list, String rawErpId, String rawName, int... marks) {
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

        if (marks == null || marks.length == 0) {
            return new OperationResult(false, "Invalid marks. Please enter marks for at least 1 subject.", null);
        }

        for (int i = 0; i < marks.length; i++) {
            if (marks[i] < 0 || marks[i] > 100) {
                return new OperationResult(false, "Invalid marks. Each mark must be between 0 and 100.", null);
            }
        }

        StudentResult newResult = new StudentResult(resolvedId, name, marks);
        list.add(newResult);
        updateNextIdNumber(list);

        String message = "Result added. ID: " + newResult.toFullDetailString();
        return new OperationResult(true, message, newResult);
    }

    /**
     * Adds a student result with explicit custom subject names and corresponding marks (0 - 100).
     */
    public static OperationResult addResultWithNamedSubjects(
            ArrayList<StudentResult> list, String rawErpId, String rawName, String[] rawSubjectNames, String[] rawMarkStrings) {
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

        String cleanName = (rawName == null) ? "" : rawName.replace(",", " ").replace("|", " ").replaceAll("\\s+", " ").trim();
        if (cleanName.isEmpty()) {
            return new OperationResult(false, "Invalid name. Name cannot be empty.", null);
        }

        int len = (rawSubjectNames == null || rawMarkStrings == null)
                ? 0
                : Math.min(rawSubjectNames.length, rawMarkStrings.length);
        while (len > 0) {
            String s = (rawSubjectNames[len - 1] == null) ? "" : rawSubjectNames[len - 1].trim();
            String m = (rawMarkStrings[len - 1] == null) ? "" : rawMarkStrings[len - 1].trim();
            if (s.isEmpty() && m.isEmpty()) {
                len--;
            } else {
                break;
            }
        }

        if (len == 0) {
            return new OperationResult(false, "Please enter at least 1 subject name and its marks (0 - 100).", null);
        }

        ArrayList<String> validSubjects = new ArrayList<>();
        ArrayList<Integer> validMarks = new ArrayList<>();

        for (int i = 0; i < len; i++) {
            String sub = (rawSubjectNames[i] == null) ? ""
                    : rawSubjectNames[i].replace(",", " ").replace("|", " ").replace(":", " ").replaceAll("\\s+", " ").trim();
            String mk = (rawMarkStrings[i] == null) ? "" : rawMarkStrings[i].trim();
            if (sub.isEmpty() && mk.isEmpty()) continue;
            if (sub.isEmpty()) {
                return new OperationResult(false, "Please enter the subject name for row #" + (i + 1) + ".", null);
            }
            if (mk.isEmpty()) {
                return new OperationResult(false, "Please enter marks (0 - 100) for \"" + sub + "\".", null);
            }
            for (int k = 0; k < validSubjects.size(); k++) {
                if (validSubjects.get(k).equalsIgnoreCase(sub)) {
                    return new OperationResult(false, "Duplicate subject \"" + sub + "\".", null);
                }
            }
            int parsedMark;
            try {
                parsedMark = Integer.parseInt(mk);
            } catch (NumberFormatException e) {
                return new OperationResult(false, "Invalid marks. Each mark must be an integer between 0 and 100.", null);
            }
            if (parsedMark < 0 || parsedMark > 100) {
                return new OperationResult(false, "Invalid marks. Each mark must be between 0 and 100.", null);
            }
            validSubjects.add(sub);
            validMarks.add(parsedMark);
        }

        if (validSubjects.isEmpty()) {
            return new OperationResult(false, "Please enter at least 1 subject name and its marks (0 - 100).", null);
        }

        String[] subArr = validSubjects.toArray(new String[0]);
        int[] mkArr = new int[validMarks.size()];
        for (int i = 0; i < validMarks.size(); i++) mkArr[i] = validMarks.get(i);

        StudentResult created = new StudentResult(resolvedId, cleanName, subArr, mkArr);
        list.add(created);
        updateNextIdNumber(list);

        return new OperationResult(true, "Result added. ID: " + created.toFullDetailString(), created);
    }

    /**
     * Removes a student record by ERP ID using manual Linear Search index lookup.
     */
    public static OperationResult removeResultById(ArrayList<StudentResult> list, String targetId) {
        int idx = findIndexById(list, targetId);
        if (idx < 0) {
            return new OperationResult(false, "Result not found for ID: " + targetId, null);
        }
        StudentResult removed = list.remove(idx);
        updateNextIdNumber(list);
        return new OperationResult(true, "Removed student record: " + removed.getId() + " | " + removed.getName(), removed);
    }

    /**
     * Parses dynamic raw mark strings (ignoring trailing empty auto-grow slots, rejecting interior gaps,
     * and supporting space/comma-separated marks) and adds the student result.
     */
    public static OperationResult addResultFromInputs(
            ArrayList<StudentResult> list, String rawErpId, String rawName, String... rawMarks) {
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

        int effectiveLen = (rawMarks == null) ? 0 : rawMarks.length;
        while (effectiveLen > 0 && (rawMarks[effectiveLen - 1] == null || rawMarks[effectiveLen - 1].trim().isEmpty())) {
            effectiveLen--;
        }

        if (effectiveLen == 0) {
            return new OperationResult(false, "Invalid marks. Please enter marks for at least 1 subject.", null);
        }

        ArrayList<Integer> parsedMarks = new ArrayList<>();
        try {
            for (int i = 0; i < effectiveLen; i++) {
                String entry = (rawMarks[i] == null) ? "" : rawMarks[i].trim();
                if (entry.isEmpty()) {
                    return new OperationResult(
                            false,
                            "Please enter marks for Subject " + (i + 1) + " or clear empty gaps between subjects.",
                            null);
                }
                String[] tokens = entry.split("[,\\s]+");
                for (int k = 0; k < tokens.length; k++) {
                    if (!tokens[k].isEmpty()) {
                        parsedMarks.add(Integer.parseInt(tokens[k]));
                    }
                }
            }
        } catch (NumberFormatException e) {
            return new OperationResult(false, "Invalid marks. Each mark must be an integer between 0 and 100.", null);
        }

        int[] marksArr = new int[parsedMarks.size()];
        for (int i = 0; i < parsedMarks.size(); i++) {
            marksArr[i] = parsedMarks.get(i);
        }

        return createAndAddResult(list, rawErpId, nameCheck, marksArr);
    }

    /**
     * Feature 1: Add Result (Console interaction — supports any number of subjects >= 1)
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

        System.out.print("Enter Marks in subjects (0 - 100 each, space-separated): ");
        if (!scanner.hasNextLine()) return;
        String marksLine = scanner.nextLine().trim();
        while (marksLine.isEmpty() && scanner.hasNextLine()) {
            marksLine = scanner.nextLine().trim();
        }

        OperationResult outcome = addResultFromInputs(list, rawErp, name, marksLine);
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
     * Parses a single line from results.txt into a StudentResult object (supports dynamic N subjects).
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
                    int maxMarks = 300;
                    double percentage = 0.0;
                    String result = "Pass";
                    for (int i = 0; i < parts.length; i++) {
                        String p = parts[i];
                        if (p.startsWith("Total:")) {
                            String totalStr = p.replace("Total:", "").trim();
                            if (totalStr.contains("/")) {
                                String[] tp = totalStr.split("/");
                                total = (int) Math.round(Double.parseDouble(tp[0].trim()));
                                maxMarks = Integer.parseInt(tp[1].trim());
                            } else {
                                total = (int) Math.round(Double.parseDouble(totalStr));
                            }
                        } else if (p.startsWith("Percentage:")) {
                            String pctStr = p.replace("Percentage:", "").trim();
                            percentage = Double.parseDouble(pctStr);
                        } else if (p.startsWith("Result:")) {
                            result = p.replace("Result:", "").trim();
                        }
                    }
                    int subCount = Math.max(1, maxMarks / 100);
                    int[] marks = new int[subCount];
                    int base = total / subCount;
                    int rem = total % subCount;
                    for (int s = 0; s < subCount; s++) {
                        marks[s] = base + (s < rem ? 1 : 0);
                    }
                    return new StudentResult(id, name, null, marks, total, maxMarks, percentage, result);
                }
            } else if (line.contains(",")) {
                String[] parts = line.split(",");
                for (int i = 0; i < parts.length; i++) {
                    parts[i] = parts[i].trim();
                }

                if (parts.length >= 3) {
                    String id = normalizeLoadedId(parts[0]);
                    String name = parts[1];
                    String last = parts[parts.length - 1];
                    int markEndExclusive = parts.length;
                    if (("Pass".equalsIgnoreCase(last) || "Fail".equalsIgnoreCase(last)) && parts.length >= 6) {
                        markEndExclusive = parts.length - 3;
                    }
                    int count = markEndExclusive - 2;
                    if (count >= 1) {
                        String[] subNames = new String[count];
                        int[] marks = new int[count];
                        for (int i = 0; i < count; i++) {
                            String token = parts[2 + i];
                            int colonIdx = token.lastIndexOf(':');
                            if (colonIdx > 0) {
                                subNames[i] = token.substring(0, colonIdx).trim();
                                marks[i] = Integer.parseInt(token.substring(colonIdx + 1).trim());
                            } else {
                                subNames[i] = "Subject " + (i + 1);
                                marks[i] = Integer.parseInt(token.trim());
                            }
                        }
                        return new StudentResult(id, name, subNames, marks);
                    }
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
    private JLabel subjectsCountLabel;
    private JPanel dynamicMarksPanel;
    private final ArrayList<JTextField> dynamicSubjectNameFields = new ArrayList<>();
    private final ArrayList<JTextField> dynamicMarkFields = new ArrayList<>();
    private boolean updatingDynamicMarks = false;
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

        JLabel subtitle = new JLabel("PS No. 48  •  File: " + fileName + "  •  Subjects (/100 each)  •  Pass >= 40.00%");
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
        JPanel marksHeaderRow = new JPanel(new BorderLayout());
        marksHeaderRow.setOpaque(false);
        marksHeaderRow.add(createFieldLabel("Marks in subjects (0 - 100 each)"), BorderLayout.WEST);
        subjectsCountLabel = new JLabel("0 subjects");
        subjectsCountLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
        subjectsCountLabel.setForeground(PRIMARY);
        marksHeaderRow.add(subjectsCountLabel, BorderLayout.EAST);
        card.add(marksHeaderRow, gbc);

        dynamicMarksPanel = new JPanel(new GridLayout(0, 1, 0, 6));
        dynamicMarksPanel.setOpaque(false);
        resetDynamicMarksFields();

        gbc.gridy = 6;
        gbc.insets = new Insets(2, 4, 8, 4);
        card.add(dynamicMarksPanel, gbc);

        JPanel btnRow = new JPanel(new BorderLayout(8, 0));
        btnRow.setOpaque(false);

        JButton addBtn = createStyledButton("1 - Add Result", PRIMARY, Color.WHITE);
        JButton clearBtn = createStyledButton("Clear", new Color(226, 232, 240), TEXT_MAIN);

        ActionListener submitAddAction = e -> onAddResult();
        addBtn.addActionListener(submitAddAction);

        clearBtn.addActionListener(e -> {
            String autoErp = ExamResultManagementSystem.formatNextId();
            erpIdField.setText("ERP".equals(autoErp) ? "" : autoErp);
            nameField.setText("");
            resetDynamicMarksFields();
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
        JButton removeSelectedBtn = createStyledButton("Remove Selected Student", new Color(225, 29, 72), Color.WHITE);
        JButton saveBtn = createStyledButton("Save Now to " + fileName, new Color(22, 163, 74), Color.WHITE);
        JButton clearAllBtn = createStyledButton("Clear All Saved Records", new Color(217, 119, 6), Color.WHITE);
        JButton exitBtn = createStyledButton("5 - Save & Exit", DANGER_BTN, Color.WHITE);

        displayAllBtn.addActionListener(e -> onDisplayAll());
        sortBtn.addActionListener(e -> onSortByPercentage());
        removeSelectedBtn.addActionListener(e -> onRemoveSelectedRecord());
        saveBtn.addActionListener(e -> onSaveNow());
        clearAllBtn.addActionListener(e -> onClearAllRecords());
        exitBtn.addActionListener(e -> saveAndExit());

        card.add(title);
        card.add(displayAllBtn);
        card.add(sortBtn);
        card.add(removeSelectedBtn);
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
            "#", "ERP ID", "Student Name", "Subjects", "Total", "Percentage", "Result"
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
        applyDynamicTableColumnFormatting();

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

    private void resetDynamicMarksFields() {
        updatingDynamicMarks = true;
        try {
            dynamicSubjectNameFields.clear();
            dynamicMarkFields.clear();
            dynamicMarksPanel.removeAll();
            appendSubjectFieldSlot();
            refreshSubjectCountCaption();
            dynamicMarksPanel.revalidate();
            dynamicMarksPanel.repaint();
        } finally {
            updatingDynamicMarks = false;
        }
    }

    private void appendSubjectFieldSlot() {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);

        JTextField subNameTf = createStyledTextField("");
        subNameTf.setToolTipText("Subject name (e.g. DSA, Maths, DBMS)");

        JTextField markTf = createStyledTextField("");
        markTf.setHorizontalAlignment(JTextField.CENTER);
        markTf.setPreferredSize(new Dimension(78, 32));
        markTf.setToolTipText("Marks (0 - 100)");
        markTf.addActionListener(e -> onAddResult());

        DocumentListener dl = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                SwingUtilities.invokeLater(() -> syncDynamicMarksGrowth());
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                SwingUtilities.invokeLater(() -> syncDynamicMarksGrowth());
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                SwingUtilities.invokeLater(() -> syncDynamicMarksGrowth());
            }
        };
        subNameTf.getDocument().addDocumentListener(dl);
        markTf.getDocument().addDocumentListener(dl);

        row.add(subNameTf, BorderLayout.CENTER);
        row.add(markTf, BorderLayout.EAST);
        dynamicSubjectNameFields.add(subNameTf);
        dynamicMarkFields.add(markTf);
        dynamicMarksPanel.add(row);
    }

    private boolean isRowBlankAt(int idx) {
        return dynamicSubjectNameFields.get(idx).getText().trim().isEmpty()
                && dynamicMarkFields.get(idx).getText().trim().isEmpty();
    }

    private void syncDynamicMarksGrowth() {
        if (updatingDynamicMarks || dynamicMarkFields.isEmpty()) return;
        updatingDynamicMarks = true;
        try {
            boolean changedLayout = false;
            int lastIdx = dynamicMarkFields.size() - 1;
            if (!isRowBlankAt(lastIdx)) {
                appendSubjectFieldSlot();
                changedLayout = true;
            } else {
                while (dynamicMarkFields.size() > 1) {
                    int n = dynamicMarkFields.size();
                    boolean tailFocused = dynamicSubjectNameFields.get(n - 1).isFocusOwner()
                            || dynamicMarkFields.get(n - 1).isFocusOwner();
                    if (isRowBlankAt(n - 1) && isRowBlankAt(n - 2) && !tailFocused) {
                        dynamicSubjectNameFields.remove(n - 1);
                        dynamicMarkFields.remove(n - 1);
                        dynamicMarksPanel.remove(n - 1);
                        changedLayout = true;
                    } else {
                        break;
                    }
                }
            }
            refreshSubjectCountCaption();
            if (changedLayout) {
                dynamicMarksPanel.revalidate();
                dynamicMarksPanel.repaint();
            }
        } finally {
            updatingDynamicMarks = false;
        }
    }

    private void refreshSubjectCountCaption() {
        if (subjectsCountLabel == null) return;
        int filled = 0;
        for (int i = 0; i < dynamicMarkFields.size(); i++) {
            if (!isRowBlankAt(i)) {
                filled++;
            }
        }
        if (filled == 0) {
            subjectsCountLabel.setText("0 subjects");
        } else if (filled == 1) {
            subjectsCountLabel.setText("1 subject (/100)");
        } else {
            subjectsCountLabel.setText(filled + " subjects (/" + (filled * 100) + ")");
        }
    }

    private void onAddResult() {
        String rawErp = erpIdField.getText();
        String rawName = nameField.getText();
        String[] rawSubNames = new String[dynamicSubjectNameFields.size()];
        String[] rawMarks = new String[dynamicMarkFields.size()];
        for (int i = 0; i < dynamicMarkFields.size(); i++) {
            rawSubNames[i] = dynamicSubjectNameFields.get(i).getText();
            rawMarks[i] = dynamicMarkFields.get(i).getText();
        }

        OperationResult outcome = ExamResultManagementSystem.addResultWithNamedSubjects(
                resultsList, rawErp, rawName, rawSubNames, rawMarks);
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
            resetDynamicMarksFields();
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

    private void onRemoveSelectedRecord() {
        int selectedRow = resultsTable.getSelectedRow();
        if (selectedRow < 0 || selectedRow >= resultsList.size()) {
            setBanner("Select a student row in the table first to remove it.", false, true);
            return;
        }
        String targetId = resultsList.get(selectedRow).getId();
        OperationResult rem = ExamResultManagementSystem.removeResultById(resultsList, targetId);
        if (rem.isSuccess()) {
            ExamResultManagementSystem.saveResultsToFile(resultsList, fileName);
            refreshTable(false);
            updateStats();
            appendLog("> " + rem.getMessage());
            setBanner(rem.getMessage(), true, false);
        }
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

    private void applyDynamicTableColumnFormatting() {
        int colCount = resultsTable.getColumnModel().getColumnCount();
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);

        for (int i = 0; i < colCount; i++) {
            int width = 64;
            if (i == 0) width = 38;
            else if (i == 1) width = 96;
            else if (i == 2) width = 160;
            else if (i == colCount - 3) width = 88;
            else if (i == colCount - 2) width = 90;
            else if (i == colCount - 1) width = 76;

            resultsTable.getColumnModel().getColumn(i).setPreferredWidth(width);
            if (i != 2 && i != colCount - 1) {
                resultsTable.getColumnModel().getColumn(i).setCellRenderer(centerRenderer);
            }
        }

        if (colCount > 0) {
            resultsTable.getColumnModel().getColumn(colCount - 1).setCellRenderer(new DefaultTableCellRenderer() {
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
        }
    }

    private void refreshTable(boolean sortedView) {
        ArrayList<String> uniqueSubjectCols = new ArrayList<>();
        for (int i = 0; i < resultsList.size(); i++) {
            String[] sNames = resultsList.get(i).getSubjectNames();
            for (int j = 0; j < sNames.length; j++) {
                boolean exists = false;
                for (int k = 0; k < uniqueSubjectCols.size(); k++) {
                    if (uniqueSubjectCols.get(k).equalsIgnoreCase(sNames[j])) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    uniqueSubjectCols.add(sNames[j]);
                }
            }
        }

        ArrayList<String> cols = new ArrayList<>();
        cols.add("#");
        cols.add("ERP ID");
        cols.add("Student Name");
        if (uniqueSubjectCols.isEmpty()) {
            cols.add("Subjects");
        } else {
            for (int s = 0; s < uniqueSubjectCols.size(); s++) {
                cols.add(uniqueSubjectCols.get(s));
            }
        }
        cols.add("Total");
        cols.add("Percentage");
        cols.add("Result");

        tableModel.setDataVector(new Object[0][0], cols.toArray(new String[0]));
        applyDynamicTableColumnFormatting();

        for (int i = 0; i < resultsList.size(); i++) {
            StudentResult r = resultsList.get(i);
            Object[] row = new Object[cols.size()];
            row[0] = i + 1;
            row[1] = r.getId();
            row[2] = r.getName();
            for (int s = 0; s < uniqueSubjectCols.size(); s++) {
                Integer m = r.findMarkForSubject(uniqueSubjectCols.get(s));
                row[3 + s] = (m != null) ? m : "—";
            }
            int tailBase = 3 + (uniqueSubjectCols.isEmpty() ? 1 : uniqueSubjectCols.size());
            row[tailBase] = r.getTotal() + "/" + r.getMaxMarks();
            row[tailBase + 1] = String.format(Locale.US, "%.2f", r.getPercentage());
            row[tailBase + 2] = r.getResult();
            tableModel.addRow(row);
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
