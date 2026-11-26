// Exam Result Management System — Smooth Frontend Controller
// Connects to /api/results (Netlify Serverless Function or Java HTTP Backend),
// with seamless localStorage fallback when opened offline.
// Auto-detects next ERP ID from saved records (displays just "ERP" when 0 records exist).

const LOCAL_STORAGE_KEY = "exam_results_erp_v2";

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
const statusBanner = document.getElementById("statusBanner");
const statusBannerText = document.getElementById("statusBannerText");
const tableMetaLabel = document.getElementById("tableMetaLabel");
const resultsTableBody = document.getElementById("resultsTableBody");
const consoleBox = document.getElementById("consoleBox");

const erpIdInput = document.getElementById("erpIdInput");
const studentNameInput = document.getElementById("studentName");
const mark1Input = document.getElementById("mark1");
const mark2Input = document.getElementById("mark2");
const mark3Input = document.getElementById("mark3");
const searchIdInput = document.getElementById("searchIdInput");

// ---------- Offline Fallback DSA Engine (mirrors backend identically) ----------
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
  return `${r.id} | ${r.name} | Total: ${r.total}/300 | Percentage: ${Number(r.percentage).toFixed(2)} | Result: ${r.result}`;
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

function loadLocalFallback() {
  try {
    const raw = localStorage.getItem(LOCAL_STORAGE_KEY);
    return raw ? JSON.parse(raw) : [];
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
    const p1 = String(payload.m1 || "").trim();
    const p2 = String(payload.m2 || "").trim();
    const p3 = String(payload.m3 || "").trim();
    let m1, m2, m3;
    if (p1 && !p2 && !p3 && /[,\s]+/.test(p1)) {
      const t = p1.split(/[,\s]+/);
      if (t.length !== 3) return buildClientPayload(list, false, "Invalid marks. Please enter exactly 3 marks.", -1);
      [m1, m2, m3] = t.map(Number);
    } else {
      if (!p1 || !p2 || !p3) return buildClientPayload(list, false, "Invalid marks. Please enter marks for all 3 subjects.", -1);
      [m1, m2, m3] = [Number(p1), Number(p2), Number(p3)];
    }
    if (!Number.isInteger(m1) || !Number.isInteger(m2) || !Number.isInteger(m3)) {
      return buildClientPayload(list, false, "Invalid marks. Each mark must be an integer between 0 and 100.", -1);
    }
    if (m1 < 0 || m1 > 100 || m2 < 0 || m2 > 100 || m3 < 0 || m3 > 100) {
      return buildClientPayload(list, false, "Invalid marks. Each mark must be between 0 and 100.", -1);
    }
    const total = m1 + m2 + m3;
    const percentage = Number(((total * 100.0) / 300.0).toFixed(2));
    const result = (total * 100.0) / 300.0 >= 40.0 ? "Pass" : "Fail";
    const rec = { id, name, mark1: m1, mark2: m2, mark3: m3, total, percentage, result };
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

function renderState(data, { highlightIndex = -1, metaLabel = null, syncErpInput = true } = {}) {
  appRecords = data.records || [];
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

  if (appRecords.length === 0) {
    resultsTableBody.innerHTML = `
      <tr class="empty-row">
        <td colspan="9">No student results saved yet. Enter an ERP ID, student name, and marks on the left to begin.</td>
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

    tr.innerHTML = `
      <td class="center">${i + 1}</td>
      <td class="center erp-cell">${escapeHtml(r.id)}</td>
      <td>${escapeHtml(r.name)}</td>
      <td class="center">${r.mark1}</td>
      <td class="center">${r.mark2}</td>
      <td class="center">${r.mark3}</td>
      <td class="center"><strong>${r.total}</strong>/300</td>
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

// Auto-split pasted "80 70 90" in Subject 1 into Subject 1, 2, 3 smoothly
mark1Input.addEventListener("input", () => {
  const val = mark1Input.value.trim();
  if (/[,\s]+/.test(val) && !mark2Input.value.trim() && !mark3Input.value.trim()) {
    const parts = val.split(/[,\s]+/);
    if (parts.length === 3) {
      mark1Input.value = parts[0];
      mark2Input.value = parts[1];
      mark3Input.value = parts[2];
      mark3Input.focus();
    }
  }
});

// ---------- Event Listeners ----------
document.getElementById("addResultForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  const payload = {
    action: "add",
    erpId: erpIdInput.value,
    name: studentNameInput.value,
    m1: mark1Input.value,
    m2: mark2Input.value,
    m3: mark3Input.value,
  };
  const data = await callBackend("POST", payload);
  appendConsole("> Menu Option 1 (Add Result)");
  appendConsole(data.message);

  if (data.success) {
    renderState(data, { highlightIndex: data.matchedIndex, syncErpInput: true });
    setBanner(data.message, "ok");
    showToast(`Added ${data.records[data.records.length - 1]?.id}`, "ok");
    studentNameInput.value = "";
    mark1Input.value = "";
    mark2Input.value = "";
    mark3Input.value = "";
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
  mark1Input.value = "";
  mark2Input.value = "";
  mark3Input.value = "";
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
    .map((r) => `${r.id},${r.name},${r.mark1},${r.mark2},${r.mark3},${r.total},${Number(r.percentage).toFixed(2)},${r.result}`)
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
(async function init() {
  const data = await callBackend("GET");
  renderState(data, { syncErpInput: true });
  if (data.records.length === 0) {
    setBanner("Ready. Enter the first student's ERP ID to begin — subsequent ERP IDs auto-detect automatically.", "info");
    appendConsole("System ready. 0 existing records. Enter the first ERP ID to start auto-detection.");
  } else {
    setBanner(`Loaded ${data.records.length} record(s). Auto-detected next ID: ${data.nextId}`, "ok");
    appendConsole(`Loaded ${data.records.length} record(s):`);
    appendConsole(data.records.map((r, i) => `${i + 1}. ${fullDetail(r)}`).join("\n"));
  }
})();
