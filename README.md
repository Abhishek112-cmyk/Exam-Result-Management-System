# PS No. 48: Exam Result Management System

A robust console-based application implemented in Java to manage student exam results using custom Data Structures and Algorithms (ArrayList, Custom In-Place Sorting, and Custom Linear Searching), Object-Oriented Design, and Persistent File Handling.

---

## 📋 Table of Contents
1. [Problem Statement](#-problem-statement)
2. [Bare Minimum Requirements](#-bare-minimum-requirements)
3. [Key Concepts & Algorithms](#-key-concepts--algorithms)
4. [Project Structure](#-project-structure)
5. [Class Design & Architecture](#-class-design--architecture)
6. [Compilation & Execution](#-compilation--execution)
7. [Test Cases & Expected Outputs](#-test-cases--expected-outputs)
8. [Edge Cases Handled](#-edge-cases-handled)
9. [GitHub Submission Guide](#-github-submission-guide)

---

## 🎯 Problem Statement
Develop a console application to manage exam results:
- **Add student results** with marks in three subjects (each out of 100).
- **Display all results** currently stored in memory.
- **Search by result ID** using custom searching logic.
- **Sort results by percentage (high to low)** using custom sorting logic.
- **File Persistence**: Load data from `results.txt` at program start, and automatically save data on exit.
- **Result ID Generation**: The first result ID generated is `ER101`. Subsequent IDs increment sequentially (`ER102`, `ER103`, etc.).
- **Pass Criterion**: Pass percentage is **40.0%** or higher (`Percentage >= 40.0 -> Pass`, otherwise `Fail`).

---

## 📌 Bare Minimum Requirements
1. **Single File Implementation**: Solution written in Java (`ExamResultManagementSystem.java`) and runnable from console.
2. **ArrayList Object Storage**: Each student record is modeled as an instance of `StudentResult` and stored in an `ArrayList<StudentResult>`.
3. **No Built-in Sort/Search Methods**:
   - Custom in-place Bubble Sort for sorting by percentage (high to low).
   - Custom Linear Search for looking up student records by ID.
4. **Validation Rules**:
   - Marks in each of the 3 subjects must be between 0 and 100.
   - If marks violate this rule, output: `Invalid marks. Each mark must be between 0 and 100.` (and do not allocate or consume an ID).
5. **Persistence**:
   - Load existing data from `results.txt` at startup.
   - Automatically save data on exit (including normal menu exit and JVM shutdown hooks).

---

## ⚙️ Key Concepts & Algorithms

### 1. Data Storage: Dynamic Array (`ArrayList<StudentResult>`)
- Dynamic resizing with amortized $O(1)$ insertions.
- Allows direct index access $O(1)$ for custom sorting swaps and linear iterations.

### 2. Searching: Custom Linear Search (`searchById`)
- **Algorithm**: Sequentially scans the `ArrayList` from index `0` to `n - 1`.
- **Case Insensitivity**: Normalizes input using `.equalsIgnoreCase()` to support `ER101`, `er101`, etc.
- **Time Complexity**:
  - Best Case: $O(1)$ (target at index 0)
  - Worst Case: $O(n)$ (target at end or not present)
  - Average Case: $O(n)$
- **Space Complexity**: $O(1)$ auxiliary space.

```java
public static StudentResult searchById(ArrayList<StudentResult> list, String searchId) {
    if (list == null || searchId == null) return null;
    String target = searchId.trim();
    for (int i = 0; i < list.size(); i++) {
        StudentResult current = list.get(i);
        if (current.getId().equalsIgnoreCase(target)) {
            return current;
        }
    }
    return null;
}
```

### 3. Sorting: Custom In-Place Bubble Sort (`sortByPercentage`)
- **Algorithm**: Compares adjacent student percentages and swaps them if the previous is smaller than the next (sorting in descending order from high to low).
- **Optimization**: An early-exit `swapped` boolean flag terminates outer passes early if the array is already sorted ($O(n)$ best case).
- **Stability**: Stable sort that preserves original insertion order for students with identical percentages.
- **Time Complexity**:
  - Best Case: $O(n)$ (already sorted)
  - Worst Case: $O(n^2)$ (reverse sorted)
  - Average Case: $O(n^2)$
- **Space Complexity**: $O(1)$ auxiliary space (in-place).

```java
public static void sortByPercentage(ArrayList<StudentResult> list) {
    if (list == null || list.size() <= 1) return;
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
        if (!swapped) break;
    }
}
```

---

## 📂 Project Structure
```
.
├── ExamResultManagementSystem.java       # Main source file (single file solution)
├── ExamResultManagementSystemTest.java   # Automated unit and integration test suite
├── run.sh                                # One-click compile & run (macOS)
├── test.sh                               # One-click compile & run tests
├── results.txt                           # Data file (auto-created on first exit, not committed)
├── .gitignore                            # Git ignore rules for build artifacts
└── README.md                             # Complete project documentation
```

---

## 🏗️ Class Design & Architecture

### `StudentResult`
- **Attributes**:
  - `id` (`String`): Sequential ID (e.g., `ER101`)
  - `name` (`String`): Student's full name
  - `mark1`, `mark2`, `mark3` (`int`): Subject marks (0–100)
  - `total` (`int`): Sum of marks ($mark_1 + mark_2 + mark_3$)
  - `percentage` (`double`): $(total \times 100.0) / 300.0$
  - `result` (`String`): `"Pass"` if $percentage \ge 40.0$, else `"Fail"`
- **Formatting Methods**:
  - `toFullDetailString()`: `ER101 | Kiran Patel | Total: 240/300 | Percentage: 80.00 | Result: Pass`
  - `toSortString()`: `ER101 | Kiran Patel | 80.00 | Pass`
  - `toFileString()`: CSV representation for `results.txt`

### `ExamResultManagementSystem`
- `main(String[] args)`: Core interactive console REPL loop with automatic file loading and shutdown hooks.
- `addResult(Scanner, ArrayList)`: Input reader, mark validator (0–100), ID generator, and list insertion.
- `displayAll(ArrayList)`: Full listing of all stored results.
- `searchById(ArrayList, String)`: Custom linear search algorithm.
- `sortByPercentage(ArrayList)`: Custom descending bubble sort algorithm.
- `loadResultsFromFile(ArrayList, String)`: Multi-format line parser for initial file load.
- `saveResultsToFile(ArrayList, String)`: Serializes records to `results.txt`.

---

## 💻 Compilation & Execution

### Prerequisites
- Java Development Kit (JDK 8 or higher; tested on JDK 26)

### 1. Compile
```bash
javac ExamResultManagementSystem.java
```

### 2. Run Application
```bash
java ExamResultManagementSystem
```

### 3. Run Automated Tests
```bash
javac ExamResultManagementSystemTest.java ExamResultManagementSystem.java
java ExamResultManagementSystemTest
```

---

## 🧪 Test Cases & Expected Outputs

### Test Case 1: Normal Input
- **Input**:
  - Option `1`
  - Name: `Kiran Patel`
  - Marks: `80 70 90`
- **Expected Output**:
  ```
  Result added. ID: ER101 | Kiran Patel | Total: 240/300 | Percentage: 80.00 | Result: Pass
  ```

### Test Case 2: Boundary / Minimum Input
- **Input**:
  - Option `1`
  - Name: `Lalit Soni`
  - Marks: `40 40 40`
- **Expected Output**:
  ```
  Result added. ID: ER101 | Lalit Soni | Total: 120/300 | Percentage: 40.00 | Result: Pass
  ```

### Test Case 3: Invalid Input
- **Input**:
  - Option `1`
  - Name: `Kiran Patel`
  - Marks: `80 105 90`
- **Expected Output**:
  ```
  Invalid marks. Each mark must be between 0 and 100.
  ```

### Test Case 4: Different Valid Scenario
- **Pre-existing**: `ER101 | Kiran Patel | 80.00 | Pass`
- **Step (a) Option 1**: Name: `Mitali Roy`, Marks: `35 30 38`
  ```
  Result added. ID: ER102 | Mitali Roy | Total: 103/300 | Percentage: 34.33 | Result: Fail
  ```
- **Step (b) Option 4 (Sort by percentage)**:
  ```
  1. ER101 | Kiran Patel | 80.00 | Pass
  2. ER102 | Mitali Roy | 34.33 | Fail
  ```
- **Step (c) Option 3 (Search)**: ID: `ER102`
  ```
  Found: ER102 | Mitali Roy | Total: 103/300 | Percentage: 34.33 | Result: Fail
  ```

---

## 🛡️ Edge Cases Handled
1. **Negative Marks**: e.g., `-5 50 60` are caught by validation and safely rejected.
2. **Marks > 100**: e.g., `80 105 90` are rejected without consuming or advancing the ID counter.
3. **ID Sequence Resiliency**: Correctly tracks existing IDs from `results.txt` and continues from the maximum ID.
4. **Flexible Input Formats**: Accepts marks entered with spaces (`80 70 90`), commas (`80, 70, 90`), or multiple lines.
5. **Non-Existent Search Target**: Displays `Result not found for ID: <ID>`.
6. **Graceful EOF & Shutdown**: JVM shutdown hook guarantees saving to `results.txt` even on unexpected termination or piped input.

---

## 🚀 GitHub Submission Guide

Follow these simple commands to push your project to your GitHub repository:

```bash
# 1. Initialize git repository (if not already done)
git init

# 2. Stage all project files
git add .

# 3. Create initial commit
git commit -m "feat: complete Exam Result Management System (PS No. 48) with full tests and documentation"

# 4. Set your default branch to main
git branch -M main

# 5. Add your GitHub remote repository URL
git remote add origin https://github.com/<YOUR_GITHUB_USERNAME>/<YOUR_REPOSITORY_NAME>.git

# 6. Push to GitHub
git push -u origin main
```
