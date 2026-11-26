// Exam Result Management System — Smooth Frontend Controller
// Connects to /api/results (Netlify Serverless Function or Java HTTP Backend),
// with seamless localStorage fallback when opened offline.
// Supports dynamic auto-growing & auto-shrinking subjects and auto-detected ERP IDs.

const LOCAL_STORAGE_KEY = "exam_results_erp_v3";

let appRecords = [];
let nextErpId = "ERP";
let backendOnline = true;

// DOM references
const statNextErp = document.getElementById("statNextErp");
const statTotal = document.getElementById("statTotal");
const statPass = document.getElementById("statPass");
const statFail = document.getElementById("statFail");
const formErpBadge = document.getElementById("formErpBadge");
const erpHintLabel = document.getElementById("erpHintLabel");
const subjectsCountPill = document.getElementById("subjectsCountPill");
const marksGrid = document.getElementById("marksGrid");
const statusBanner = document.getElementById("statusBanner");
const statusBannerText = document.getElementById("statusBannerText");
const tableMetaLabel = document.getElementById("tableMetaLabel");
const resultsTableHeadRow = document.getElementById("resultsTableHeadRow");
const resultsTableBody = document.getElementById("resultsTableBody");
const consoleBox = document.getElementById("consoleBox");

const erpIdInput = document.getElementById("erpIdInput");
const studentNameInput = document.getElementById("studentName");
const searchIdInput = document.getElementById("searchIdInput");

// ---------- Dynamic Auto-Grow / Auto-Shrink Subject Inputs ----------
function createMarkCell(subjectIndex, initialValue = "", isOptionalSlot = false) {
  const cell = document.createElement("div");
  cell.className = isOptionalSlot ? "mark-cell optional-slot" : "mark-cell";

  const label = document.createElement("span");
  label.textContent = `Subject ${subjectIndex}`;

  const input = document.createElement("input");
  input.className = "text-input mark-input";
  input.type = "text";
  input.inputMode = "numeric";
  input.placeholder = isOptionalSlot ? "+ add" : "0-100";
  input.value = initialValue;

  input.addEventListener("input", () => handleMarkInputChange(input));
  input.addEventListener("blur", () => pruneTrailingBlankMarkCells(false));

  cell.appendChild(label);
  cell.appendChild(input);
  return cell;
}

function getMarkInputs() {
  return Array.from(marksGrid.querySelectorAll(".mark-input"));
}

function refreshMarkLabelsAndCount() {
  const cells = Array.from(marksGrid.querySelectorAll(".mark-cell"));
  let filledCount = 0;

  for (let i = 0; i < cells.length; i++) {
    const span = cells[i].querySelector("span");
    const inp = cells[i].querySelector(".mark-input");
    if (span) span.textContent = `Subject ${i + 1}`;
    const hasVal = inp && inp.value.trim() !== "";
    if (hasVal) filledCount++;

    const isTrailingEmpty = i > 0 && i === cells.length - 1 && !hasVal;
    cells[i].classList.toggle("optional-slot", isTrailingEmpty);
    if (inp) {
      inp.placeholder = isTrailingEmpty ? "+ add" : "0-100";
    }
  }

  if (filledCount === 0) {
    subjectsCountPill.textContent = "0 subjects";
  } else if (filledCount === 1) {
    subjectsCountPill.textContent = "1 subject (/100)";
  } else {
    subjectsCountPill.textContent = `${filledCount} subjects (/${filledCount * 100})`;
  }
}

function handleMarkInputChange(changedInput) {
  const rawVal = changedInput.value;

  // Support pasting space/comma-separated marks like "80 70 90 85 95" into any box
  if (/[,\s]+/.test(rawVal.trim())) {
    const parts = rawVal.trim().split(/[,\s]+/).filter(Boolean);
    if (parts.length > 1) {
      const inputs = getMarkInputs();
      const startIdx = inputs.indexOf(changedInput);
      if (startIdx !== -1) {
        for (let k = 0; k < parts.length; k++) {
          const targetIdx = startIdx + k;
          const currentInputs = getMarkInputs();
          if (targetIdx < currentInputs.length) {
            currentInputs[targetIdx].value = parts[k];
          } else {
            marksGrid.appendChild(createMarkCell(currentInputs.length + 1, parts[k], false));
          }
        }
        const afterInputs = getMarkInputs();
        if (afterInputs[afterInputs.length - 1].value.trim() !== "") {
          marksGrid.appendChild(createMarkCell(afterInputs.length + 1, "", true));
        }
        pruneTrailingBlankMarkCells(true);
        refreshMarkLabelsAndCount();
        const updatedInputs = getMarkInputs();
        const focusIdx = Math.min(startIdx + parts.length - 1, updatedInputs.length - 1);
        updatedInputs[focusIdx].focus();
        return;
      }
    }
  }

  const inputs = getMarkInputs();
  const lastInput = inputs[inputs.length - 1];

  // Auto-Grow: as soon as the last box has a value, append the next Subject slot
  if (lastInput && lastInput.value.trim() !== "") {
    marksGrid.appendChild(createMarkCell(inputs.length + 1, "", true));
  } else {
    // Auto-Shrink: collapse extra empty trailing boxes automatically
    pruneTrailingBlankMarkCells(true);
  }

  refreshMarkLabelsAndCount();
}

function pruneTrailingBlankMarkCells(protectFocused) {
  let cells = Array.from(marksGrid.querySelectorAll(".mark-cell"));
  while (cells.length > 1) {
    const lastCell = cells[cells.length - 1];
    const prevCell = cells[cells.length - 2];
    const lastInput = lastCell.querySelector(".mark-input");
    const prevInput = prevCell.querySelector(".mark-input");

    if (
      lastInput.value.trim() === "" &&
      prevInput.value.trim() === "" &&
      (!protectFocused || document.activeElement !== lastInput)
    ) {
      lastCell.remove();
      cells.pop();
    } else {
      break;
    }
  }
  refreshMarkLabelsAndCount();
}

function resetMarksGrid() {
  marksGrid.innerHTML = "";
  marksGrid.appendChild(createMarkCell(1, "", false));
  refreshMarkLabelsAndCount();
}

function collectRawMarkValues() {
  return getMarkInputs().map((inp) => inp.value.trim());
}

// ---------- Record Normalization & Offline Fallback Engine ----------
function normalizeRecordShape(r) {
  const marks = Array.isArray(r.marks)
    ? r.marks.map(Number)
    : [r.mark1, r.mark2, r.mark3].filter((v) => v !== undefined && v !== null).map(Number);
  const maxMarks = r.maxMarks || marks.length * 100;
  return {
    id: r.id,
    name: r.name,
    marks,
    total: r.total,
    maxMarks,
    percentage: Number(r.percentage),
    result: r.result,
  };
}

function parseErpInput(raw) {
  const trimmed = String(raw || "").trim();
  if (!trimmed) return null;
  const digits = trimmed.replace(/[^0-9]/g, "");
  if (!digits) return null;
  const n = parseInt(digits, 10);
  if (Number.isNaN(n) || n < 0) return null;
  return `ERP ${n}`;
}

function normalizeIdKey(raw) {
  const cleaned = String(raw || "").trim().toUpperCase().replace(/\s+/g, "");
  if (cleaned.startsWith("ERP")) return "ERP" + cleaned.slice(3);
  if (cleaned.startsWith("ER")) return "ERP" + cleaned.slice(2);
  if (/^\d+$/.test(cleaned)) return "ERP" + cleaned;
  return cleaned;
}

function computeNextId(list) {
  if (!list || list.length === 0) return "ERP";
  let max = null;
  for (let i = 0; i < list.length; i++) {
    const digits = String(list[i].id || "").replace(/[^0-9]/g, "");
    if (digits) {
      const n = parseInt(digits, 10);
      if (!Number.isNaN(n) && (max === null || n > max)) max = n;
    }
  }
  return max !== null ? `ERP ${max + 1}` : "ERP";
}

function fullDetail(r) {
  const norm = normalizeRecordShape(r);
  return `${norm.id} | ${norm.name} | Total: ${norm.total}/${norm.maxMarks} | Percentage: ${norm.percentage.toFixed(2)} | Result: ${norm.result}`;
}

function sortDetail(r) {
  return `${r.id} | ${r.name} | ${Number(r.percentage).toFixed(2)} | ${r.result}`;
}

function linearSearchLocal(list, query) {
  const target = normalizeIdKey(query);
  if (!target || target === "ERP") return { record: null, index: -1 };
  for (let i = 0; i < list.length; i++) {
    if (normalizeIdKey(list[i].id) === target) {
      return { record: list[i], index: i };
    }
  }
  return { record: null, index: -1 };
}

function bubbleSortLocal(list) {
  const n = list.length;
  for (let i = 0; i < n - 1; i++) {
    let swapped = false;
    for (let j = 0; j < n - i - 1; j++) {
      if (list[j].percentage < list[j + 1].percentage) {
        const tmp = list[j];
        list[j] = list[j + 1];
        list[j + 1] = tmp;
        swapped = true;
      }
    }
    if (!swapped) break;
  }
}

function parseMarksClient(rawArr) {
  const entries = Array.isArray(rawArr) ? rawArr.map((v) => String(v ?? "").trim()) : [];
  while (entries.length > 0 && entries[entries.length - 1] === "") {
    entries.pop();
  }
  if (entries.length === 0) {
    return { ok: false, marks: [], error: "Invalid marks. Please enter marks for at least 1 subject." };
  }
  const tokens = [];
  for (let i = 0; i < entries.length; i++) {
    if (entries[i] === "") {
      return {
        ok: false,
        marks: [],
        error: `Please enter marks for Subject ${i + 1} or clear empty gaps between subjects.`,
      };
    }
    const split = entries[i].split(/[,\s]+/).filter(Boolean);
    for (let k = 0; k < split.length; k++) tokens.push(split[k]);
  }
  const marks = [];
  for (let i = 0; i < tokens.length; i++) {
    const n = Number(tokens[i]);
    if (!Number.isInteger(n)) {
      return { ok: false, marks: [], error: "Invalid marks. Each mark must be an integer between 0 and 100." };
    }
    if (n < 0 || n > 100) {
      return { ok: false, marks: [], error: "Invalid marks. Each mark must be between 0 and 100." };
    }
    marks.push(n);
  }
  return { ok: true, marks, error: "" };
}

function loadLocalFallback() {
  try {
    const raw = localStorage.getItem(LOCAL_STORAGE_KEY);
    return raw ? JSON.parse(raw).map(normalizeRecordShape) : [];
  } catch {
    return [];
  }
}

function saveLocalFallback(list) {
  try {
    localStorage.setItem(LOCAL_STORAGE_KEY, JSON.stringify(list));
  } catch {}
}

// ---------- API Client with Seamless Fallback ----------
async function callBackend(method, payload) {
  try {
    const opts = { method, headers: {} };
    if (payload) {
      opts.headers["Content-Type"] = "application/json";
      opts.body = JSON.stringify(payload);
    }
    const res = await fetch("/api/results", opts);
    const data = await res.json();
    backendOnline = true;
    if (Array.isArray(data.records)) {
      data.records = data.records.map(normalizeRecordShape);
      saveLocalFallback(data.records);
    }
    return data;
  } catch {
    backendOnline = false;
    return runLocalFallbackAction(method, payload);
  }
}

function runLocalFallbackAction(method, payload) {
  const list = loadLocalFallback();
  if (method === "GET") {
    return buildClientPayload(list, true, "Ready.", -1);
  }
  const action = String(payload?.action || "").toLowerCase();
  if (action === "add") {
    const rawErp = String(payload.erpId || "").trim();
    let id = null;
    if (rawErp && rawErp.toUpperCase() !== "ERP") {
      id = parseErpInput(rawErp);
      if (!id) {
        return buildClientPayload(list, false, "Invalid ERP ID. Please enter a valid ERP number.", -1);
      }
    } else {
      const autoId = computeNextId(list);
      if (autoId === "ERP") {
        return buildClientPayload(
          list,
          false,
          "Please enter the first ERP ID so the system can auto-detect subsequent ERP IDs.",
          -1
        );
      }
      id = autoId;
    }

    if (linearSearchLocal(list, id).record !== null) {
      return buildClientPayload(list, false, `Duplicate ERP ID (${id}). A record with this ID already exists.`, -1);
    }

    const name = String(payload.name || "").replace(/[,|]/g, " ").replace(/\s+/g, " ").trim();
    if (!name) return buildClientPayload(list, false, "Invalid name. Name cannot be empty.", -1);

    const parsedMarks = parseMarksClient(payload.marks);
    if (!parsedMarks.ok) {
      return buildClientPayload(list, false, parsedMarks.error, -1);
    }

    let total = 0;
    for (let i = 0; i < parsedMarks.marks.length; i++) total += parsedMarks.marks[i];
    const maxMarks = parsedMarks.marks.length * 100;
    const rawPct = (total * 100.0) / maxMarks;
    const percentage = Number(rawPct.toFixed(2));
    const result = rawPct >= 40.0 ? "Pass" : "Fail";
    const rec = { id, name, marks: parsedMarks.marks, total, maxMarks, percentage, result };
    list.push(rec);
    saveLocalFallback(list);
    return buildClientPayload(list, true, `Result added. ID: ${fullDetail(rec)}`, list.length - 1);
  }
  if (action === "search") {
    const q = String(payload.id || "").trim();
    const { record, index } = linearSearchLocal(list, q);
    if (record) return buildClientPayload(list, true, `Found: ${fullDetail(record)}`, index);
    return buildClientPayload(list, false, `Result not found for ID: ${q}`, -1);
  }
  if (action === "sort") {
    if (!list.length) return buildClientPayload(list, false, "No results found.", -1);
    bubbleSortLocal(list);
    saveLocalFallback(list);
    const lines = list.map((r, idx) => `${idx + 1}. ${sortDetail(r)}`).join("\n");
    return buildClientPayload(list, true, lines, 0);
  }
  if (action === "clear") {
    list.length = 0;
    saveLocalFallback(list);
    return buildClientPayload(list, true, "Cleared all saved records. Auto-detected ERP ID reset to ERP.", -1);
  }
  return buildClientPayload(list, false, "Unsupported action.", -1);
}

function buildClientPayload(list, success, message, matchedIndex) {
  let pass = 0;
  for (let i = 0; i < list.length; i++) if (list[i].result === "Pass") pass++;
  return {
    success,
    message,
    nextId: computeNextId(list),
    matchedIndex,
    stats: { total: list.length, pass, fail: list.length - pass },
    records: list,
  };
}

// ---------- UI Rendering Helpers ----------
function setBanner(text, variant = "info") {
  statusBanner.className = `status-banner ${variant}`;
  statusBannerText.textContent = text;
}

function appendConsole(text) {
  if (!consoleBox.textContent) {
    consoleBox.textContent = text;
  } else {
    consoleBox.textContent += "\n" + text;
  }
  consoleBox.scrollTop = consoleBox.scrollHeight;
}

function showToast(text, variant = "ok") {
  const stack = document.getElementById("toastStack");
  const el = document.createElement("div");
  el.className = `toast ${variant}`;
  el.textContent = text;
  stack.appendChild(el);
  setTimeout(() => {
    el.style.opacity = "0";
    el.style.transition = "opacity 0.25s ease";
    setTimeout(() => el.remove(), 260);
  }, 2800);
}

function bumpStat(el) {
  const box = el.closest(".stat-box");
  if (!box) return;
  box.classList.remove("bump");
  void box.offsetWidth;
  box.classList.add("bump");
  setTimeout(() => box.classList.remove("bump"), 250);
}

function syncErpFormField() {
  if (appRecords.length === 0 || nextErpId === "ERP") {
    erpHintLabel.textContent = "— enter first ID";
    erpIdInput.placeholder = "Enter ERP ID";
    erpIdInput.value = "";
  } else {
    erpHintLabel.textContent = "— auto-detected";
    erpIdInput.placeholder = nextErpId;
    erpIdInput.value = nextErpId;
  }
}

function getMaxSubjectsCount(records) {
  let maxSub = 0;
  for (let i = 0; i < records.length; i++) {
    const len = Array.isArray(records[i].marks) ? records[i].marks.length : 0;
    if (len > maxSub) maxSub = len;
  }
  return maxSub;
}

function renderDynamicTableHeader(maxSubCount) {
  if (maxSubCount <= 0) {
    resultsTableHeadRow.innerHTML = `
      <th class="center" style="width:48px;">#</th>
      <th class="center" style="width:115px;">ERP ID</th>
      <th>Student Name</th>
      <th class="center">Subjects</th>
      <th class="center" style="width:105px;">Total</th>
      <th class="center" style="width:108px;">Percentage</th>
      <th class="center" style="width:94px;">Result</th>
    `;
    return;
  }

  let subHeaders = "";
  for (let s = 1; s <= maxSubCount; s++) {
    subHeaders += `<th class="center" style="min-width:68px;">Sub ${s}</th>`;
  }

  resultsTableHeadRow.innerHTML = `
    <th class="center" style="width:48px;">#</th>
    <th class="center" style="width:115px;">ERP ID</th>
    <th style="min-width:140px;">Student Name</th>
    ${subHeaders}
    <th class="center" style="min-width:98px;">Total</th>
    <th class="center" style="min-width:98px;">Percentage</th>
    <th class="center" style="width:90px;">Result</th>
  `;
}

function renderState(data, { highlightIndex = -1, metaLabel = null, syncErpInput = true } = {}) {
  appRecords = (data.records || []).map(normalizeRecordShape);
  nextErpId = data.nextId || computeNextId(appRecords);

  statNextErp.textContent = nextErpId;
  formErpBadge.textContent = nextErpId;
  statTotal.textContent = String(data.stats?.total ?? appRecords.length);
  statPass.textContent = String(data.stats?.pass ?? 0);
  statFail.textContent = String(data.stats?.fail ?? 0);
  bumpStat(statTotal);

  if (syncErpInput) {
    syncErpFormField();
  }

  tableMetaLabel.textContent = metaLabel || `Showing ${appRecords.length} record(s)`;

  const maxSubCount = getMaxSubjectsCount(appRecords);
  renderDynamicTableHeader(maxSubCount);

  if (appRecords.length === 0) {
    resultsTableBody.innerHTML = `
      <tr class="empty-row">
        <td colspan="7">No student results saved yet. Enter an ERP ID, student name, and subject marks on the left to begin.</td>
      </tr>`;
    return;
  }

  resultsTableBody.innerHTML = "";
  for (let i = 0; i < appRecords.length; i++) {
    const r = appRecords[i];
    const tr = document.createElement("tr");
    tr.style.animationDelay = `${Math.min(i * 0.025, 0.25)}s`;
    if (i === highlightIndex) tr.classList.add("matched-row");

    const pctText = Number(r.percentage).toFixed(2);
    const pillClass = r.result === "Pass" ? "pass" : "fail";

    let markCellsHtml = "";
    for (let s = 0; s < maxSubCount; s++) {
      const val = s < r.marks.length ? r.marks[s] : "—";
      markCellsHtml += `<td class="center">${val}</td>`;
    }

    tr.innerHTML = `
      <td class="center">${i + 1}</td>
      <td class="center erp-cell">${escapeHtml(r.id)}</td>
      <td>${escapeHtml(r.name)}</td>
      ${markCellsHtml}
      <td class="center"><strong>${r.total}</strong>/${r.maxMarks}</td>
      <td class="center">${pctText}</td>
      <td class="center"><span class="result-pill ${pillClass}">${r.result}</span></td>
    `;
    resultsTableBody.appendChild(tr);

    if (i === highlightIndex) {
      setTimeout(() => tr.scrollIntoView({ behavior: "smooth", block: "nearest" }), 60);
    }
  }
}

function escapeHtml(str) {
  return String(str ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

// Live preview in form badge when user types an ERP ID
erpIdInput.addEventListener("input", () => {
  const parsed = parseErpInput(erpIdInput.value);
  formErpBadge.textContent = parsed || nextErpId;
});

// ---------- Event Listeners ----------
document.getElementById("addResultForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  const payload = {
    action: "add",
    erpId: erpIdInput.value,
    name: studentNameInput.value,
    marks: collectRawMarkValues(),
  };
  const data = await callBackend("POST", payload);
  appendConsole("> Menu Option 1 (Add Result)");
  appendConsole(data.message);

  if (data.success) {
    renderState(data, { highlightIndex: data.matchedIndex, syncErpInput: true });
    setBanner(data.message, "ok");
    showToast(`Added ${data.records[data.records.length - 1]?.id}`, "ok");
    studentNameInput.value = "";
    resetMarksGrid();
    studentNameInput.focus();
  } else {
    setBanner(data.message, "err");
    showToast(data.message, "err");
    if (appRecords.length === 0 && !parseErpInput(erpIdInput.value)) {
      erpIdInput.focus();
    }
  }
});

document.getElementById("clearFormBtn").addEventListener("click", () => {
  studentNameInput.value = "";
  resetMarksGrid();
  syncErpFormField();
  formErpBadge.textContent = nextErpId;
  if (appRecords.length === 0) {
    erpIdInput.focus();
  } else {
    studentNameInput.focus();
  }
});

document.getElementById("searchForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  const query = searchIdInput.value.trim();
  if (!query) {
    setBanner("Please enter an ERP ID to search.", "err");
    return;
  }
  const data = await callBackend("POST", { action: "search", id: query });
  appendConsole(`> Menu Option 3 (Search by ID: ${query})`);
  appendConsole(data.message);

  if (data.success) {
    renderState(data, {
      highlightIndex: data.matchedIndex,
      metaLabel: `Search matched row #${data.matchedIndex + 1} (${appRecords[data.matchedIndex]?.id || query})`,
      syncErpInput: false,
    });
    setBanner(data.message, "ok");
  } else {
    renderState(data, { highlightIndex: -1, syncErpInput: false });
    setBanner(data.message, "err");
    showToast(data.message, "err");
  }
});

document.getElementById("displayAllBtn").addEventListener("click", async () => {
  const data = await callBackend("GET");
  renderState(data, { metaLabel: `2 – Display All (${data.records.length} record(s))` });
  appendConsole("> Menu Option 2 (Display All)");
  if (data.records.length === 0) {
    appendConsole("No results found.");
    setBanner("No results found.", "info");
  } else {
    const lines = data.records.map((r, i) => `${i + 1}. ${fullDetail(r)}`).join("\n");
    appendConsole(lines);
    setBanner(`Displaying all ${data.records.length} student result(s).`, "info");
  }
});

document.getElementById("sortBtn").addEventListener("click", async () => {
  const data = await callBackend("POST", { action: "sort" });
  appendConsole("> Menu Option 4 (Sort by Percentage - high to low)");
  appendConsole(data.message);

  if (data.success) {
    renderState(data, {
      highlightIndex: 0,
      metaLabel: "Sorted by Percentage: High to Low",
    });
    const top = data.records[0];
    setBanner(`Sorted by Percentage (high to low). Top: ${sortDetail(top)}`, "ok");
    showToast("Sorted High to Low", "ok");
  } else {
    setBanner(data.message, "info");
  }
});

document.getElementById("downloadTxtBtn").addEventListener("click", () => {
  if (!appRecords.length) {
    showToast("No records to export yet.", "err");
    return;
  }
  const content = appRecords
    .map((r) => `${r.id},${r.name},${r.marks.join(",")},${r.total},${Number(r.percentage).toFixed(2)},${r.result}`)
    .join("\n") + "\n";
  const blob = new Blob([content], { type: "text/plain;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = "results.txt";
  a.click();
  URL.revokeObjectURL(url);
  showToast("Exported results.txt", "ok");
});

// Clear All Confirmation Modal
const confirmModal = document.getElementById("confirmModal");
document.getElementById("clearAllBtn").addEventListener("click", () => {
  confirmModal.classList.add("open");
});
document.getElementById("cancelClearBtn").addEventListener("click", () => {
  confirmModal.classList.remove("open");
});
document.getElementById("confirmClearBtn").addEventListener("click", async () => {
  confirmModal.classList.remove("open");
  const data = await callBackend("POST", { action: "clear" });
  searchIdInput.value = "";
  renderState(data, { syncErpInput: true });
  appendConsole(`> ${data.message}`);
  setBanner(data.message, "ok");
  showToast("Cleared all saved records", "ok");
});

document.getElementById("clearConsoleBtn").addEventListener("click", () => {
  consoleBox.textContent = "";
});

// Theme Toggle
const themeToggleBtn = document.getElementById("themeToggleBtn");
themeToggleBtn.addEventListener("click", () => {
  const html = document.documentElement;
  const next = html.getAttribute("data-theme") === "dark" ? "light" : "dark";
  html.setAttribute("data-theme", next);
  themeToggleBtn.textContent = next === "dark" ? "☀️ Light" : "🌙 Dark";
});

// Initial Bootstrap
resetMarksGrid();
(async function init() {
  const data = await callBackend("GET");
  renderState(data, { syncErpInput: true });
  if (data.records.length === 0) {
    setBanner("Ready. Enter ERP ID, student name, and marks in as many subjects as needed.", "info");
    appendConsole("System ready. 0 existing records. Enter the first ERP ID to start auto-detection.");
  } else {
    setBanner(`Loaded ${data.records.length} record(s). Auto-detected next ID: ${data.nextId}`, "ok");
    appendConsole(`Loaded ${data.records.length} record(s):`);
    appendConsole(data.records.map((r, i) => `${i + 1}. ${fullDetail(r)}`).join("\n"));
  }
})();
