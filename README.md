# PS No. 48: Exam Result Management System

A full-stack and desktop Exam Result Management System built around custom Data Structures and Algorithms (`ArrayList`, manual **Linear Search**, and manual **Adaptive Bubble Sort**), Object-Oriented Design, and persistent storage — runnable as a **Netlify Cloud Web App**, a **Local Java Web Server**, a **Java Swing Desktop GUI**, or an **Interactive Console CLI**.

---

## 📋 Table of Contents
1. [Problem Statement](#-problem-statement)
2. [Key Features & Modes](#-key-features--modes)
3. [Key Concepts & Algorithms](#-key-concepts--algorithms)
4. [Project Structure](#-project-structure)
5. [Local Execution Guide](#-local-execution-guide)
6. [Deploy to Netlify (Cloud Web App)](#-deploy-to-netlify-cloud-web-app)
7. [Test Cases & Expected Outputs](#-test-cases--expected-outputs)

---

## 🎯 Problem Statement
Manage student exam results with:
- **Add student results** with marks in 3 subjects (each `0–100`).
- **Display all results** stored in memory.
- **Search by ERP ID** using manual Linear Search (flexible case/space/number matching).
- **Sort results by percentage (high to low)** using manual in-place Adaptive Bubble Sort.
- **Persistence**: Load existing records from `results.txt` at startup and save automatically (and via `@netlify/blobs` when deployed on Netlify).
- **Smart ERP ID Auto-Detection**: Displays **`ERP`** when empty, lets you enter the first student's ERP ID, and automatically detects and pre-fills the next sequential ERP ID (`max + 1`) for all subsequent records.
- **Pass Criterion**: `Percentage >= 40.00%` → `Pass`, otherwise `Fail`.

---

## ✨ Key Features & Modes
1. **Netlify Full-Stack Web App (`public/` + `netlify/functions/results.mts`)**:
   - Smooth modern UI with Light/Dark theme toggle, animated KPI counters, toast notifications, CSV/`results.txt` export, and instant row highlighting.
   - Serverless `/api/results` backend powered by Netlify Functions + `@netlify/blobs` strong-consistency persistence.
2. **Pure-Java Local Web Server (`./web.sh` / `--server`)**:
   - Built-in `HttpServer` inside `ExamResultManagementSystem.java` serving `public/` and `/api/results` directly against `ArrayList<StudentResult>` and `results.txt` on `http://localhost:8080`.
3. **Smooth Java Swing Desktop GUI (`./app.sh` / `--gui`)**:
   - Anti-aliased rounded cards, hover-animated buttons, live stats bar, table row highlighting, and integrated output console.
4. **Interactive Console CLI (`./run.sh`)**:
   - Classic menu-driven terminal interface (`1–5`).

---

## ⚙️ Key Concepts & Algorithms

### 1. Dynamic Storage (`ArrayList<StudentResult>`)
- Amortized $O(1)$ append and $O(1)$ random index access for in-place swaps and linear traversal.

### 2. Custom Linear Search (`searchById`)
- **Why**: Student records are appended chronologically and frequently re-ordered by percentage rather than ID, so Linear Search scans unsorted IDs directly in $O(n)$ time and $O(1)$ space without maintaining extra index structures.
- **Flexible Matching**: Normalizes `"ERP <id>"`, `"ERP<id>"`, `"erp <id>"`, and `"<id>"` to the same key.

### 3. Custom Adaptive Bubble Sort (`sortByPercentage`)
- **Why**: In-place ($O(1)$ auxiliary space), **stable** (preserves ERP chronological order when two students tie on percentage), and **adaptive** via an early-exit `swapped` flag ($O(n)$ best case when already sorted; $O(n^2)$ average/worst case).

---

## 📂 Project Structure
```
.
├── ExamResultManagementSystem.java                 # Java backend: Console CLI + Smooth Swing GUI + Local HTTP Server
├── ExamResultManagementSystemTest.java             # Automated unit & integration test suite
├── public/                                         # Smooth Responsive Web Frontend (Netlify publish dir)
│   ├── index.html                                  # Clean Web UI (Auto-Detected ERP IDs, Dark/Light mode)
│   ├── styles.css                                  # Glassmorphic styling, micro-interactions, responsive layout
│   └── app.js                                      # Frontend controller + localStorage offline fallback
├── netlify/functions/
│   └── results.mts                                 # Netlify Serverless API (/api/results) + Netlify Blobs storage
├── netlify.toml                                    # Netlify build, publish, and functions configuration
├── package.json                                    # Netlify Functions & Blobs dependencies
├── web.sh                                          # Launch local Java Web Server at http://localhost:8080
├── app.sh                                          # Launch Java Swing Desktop GUI
├── run.sh                                          # Launch Console CLI mode
├── test.sh                                         # Compile & run automated Java test suite
├── Launch_App.command                              # Double-clickable macOS launcher for Swing Desktop GUI
├── Exam_Result_Management_System_Report.pdf        # 10-page University Project Interim Report (PDF)
├── Exam_Result_Management_System_Presentation.html # 12-slide Interactive 3D Presentation Deck
├── Exam_Result_Management_System_Presentation.pdf  # 12-slide Widescreen PDF Presentation Deck
└── Launch_Presentation.command                     # Double-clickable macOS launcher for Presentation
```

---

## 💻 Local Execution Guide

### 1. Run Smooth Web App Locally (Java Backend + Web UI)
```bash
./web.sh
# Opens http://localhost:8080 backed by ArrayList<StudentResult> and results.txt
```

### 2. Run Desktop GUI Application (Java Swing)
```bash
./app.sh
```

### 3. Run Console Application (Terminal Mode)
```bash
./run.sh
```

### 4. Run Automated Test Suite
```bash
./test.sh
```

---

## ☁️ Deploy to Netlify (Cloud Web App)

This repository is pre-configured with `netlify.toml`, `public/`, and `netlify/functions/results.mts` for zero-config Netlify deployment:

1. Log in to [app.netlify.com](https://app.netlify.com) and click **Add new project** → **Import an existing project**.
2. Connect **GitHub** and select **`Abhishek112-cmyk/Exam-Result-Management-System`**.
3. Netlify automatically detects `netlify.toml`:
   - **Build command**: `npm run build`
   - **Publish directory**: `public`
   - **Functions directory**: `netlify/functions`
4. Click **Deploy project** — your live full-stack web app and `/api/results` serverless backend (with persistent Netlify Blobs storage) will be live immediately.

---

## 🧪 Test Cases & Expected Outputs

| Case | Input | Expected Output |
| :--- | :--- | :--- |
| **1. Normal Input** | First ERP `501`, `Kiran Patel`, Marks `80 70 90` | `Result added. ID: ERP 501 \| Kiran Patel \| Total: 240/300 \| Percentage: 80.00 \| Result: Pass` (Next ID auto-detects `ERP 502`) |
| **2. Boundary (40%)** | First ERP `700`, `Lalit Soni`, Marks `40 40 40` | `Result added. ID: ERP 700 \| Lalit Soni \| Total: 120/300 \| Percentage: 40.00 \| Result: Pass` |
| **3. Invalid Marks** | First ERP `801`, `Kiran Patel`, Marks `80 105 90` | `Invalid marks. Each mark must be between 0 and 100.` (No record added; badge remains `ERP`) |
| **4. Auto-Detect + Sort + Search** | Add `Mitali Roy` (`35 30 38`) after `ERP 501`, Sort, Search `ERP 502` | Auto-assigns `ERP 502`, sorts high-to-low (`ERP 501` then `ERP 502`), and outputs `Found: ERP 502 \| Mitali Roy \| Total: 103/300 \| Percentage: 34.33 \| Result: Fail` |
