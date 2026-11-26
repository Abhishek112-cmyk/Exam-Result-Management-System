import type { Config, Context } from "@netlify/functions";
import { getDeployStore, getStore } from "@netlify/blobs";

interface SubjectEntry {
  subject: string;
  mark: number;
}

interface StudentRecord {
  id: string;
  name: string;
  subjects: SubjectEntry[];
  marks: number[];
  total: number;
  maxMarks: number;
  percentage: number;
  result: "Pass" | "Fail";
}

const BLOB_KEY = "results_v4.json";
let memoryFallbackRecords: StudentRecord[] = [];

const DEFAULT_FIREBASE_DB_URL = "https://exam-result-management-system-default-rtdb.firebaseio.com";

function getFirebaseDbUrl(): string {
  const raw = (process.env.FIREBASE_DATABASE_URL ?? DEFAULT_FIREBASE_DB_URL).trim().replace(/\/+$/, "");
  return raw;
}

function formatId(num: number): string {
  return `ERP ${num}`;
}

function parseErpInput(raw: string): string | null {
  const trimmed = (raw ?? "").trim();
  if (!trimmed) return null;
  const digits = trimmed.replace(/[^0-9]/g, "");
  if (!digits) return null;
  const num = parseInt(digits, 10);
  if (Number.isNaN(num) || num < 0) return null;
  return formatId(num);
}

function normalizeIdKey(raw: string): string {
  const cleaned = (raw ?? "").trim().toUpperCase().replace(/\s+/g, "");
  if (cleaned.startsWith("ERP")) return "ERP" + cleaned.slice(3);
  if (cleaned.startsWith("ER")) return "ERP" + cleaned.slice(2);
  if (/^\d+$/.test(cleaned)) return "ERP" + cleaned;
  return cleaned;
}

function sanitizeText(raw: string): string {
  return (raw ?? "")
    .replace(/[,|:]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function createStudentRecord(id: string, name: string, subjects: SubjectEntry[]): StudentRecord {
  let total = 0;
  const marks: number[] = [];
  for (let i = 0; i < subjects.length; i++) {
    marks.push(subjects[i].mark);
    total += subjects[i].mark;
  }
  const maxMarks = subjects.length * 100;
  const rawPct = maxMarks > 0 ? (total * 100.0) / maxMarks : 0;
  const result: "Pass" | "Fail" = rawPct >= 40.0 ? "Pass" : "Fail";
  return {
    id,
    name,
    subjects,
    marks,
    total,
    maxMarks,
    percentage: Number(rawPct.toFixed(2)),
    result,
  };
}

function normalizeStoredRecord(raw: Record<string, unknown>): StudentRecord | null {
  const id = parseErpInput(String(raw.id ?? "")) ?? String(raw.id ?? "").trim();
  const name = sanitizeText(String(raw.name ?? ""));
  if (!id || !name) return null;

  const subjects: SubjectEntry[] = [];
  if (Array.isArray(raw.subjects)) {
    for (let i = 0; i < raw.subjects.length; i++) {
      const item = raw.subjects[i] as Record<string, unknown>;
      const subName = sanitizeText(String(item?.subject ?? `Subject ${i + 1}`));
      const mark = Number(item?.mark);
      if (subName && !Number.isNaN(mark)) {
        subjects.push({ subject: subName, mark });
      }
    }
  } else if (Array.isArray(raw.marks)) {
    for (let i = 0; i < raw.marks.length; i++) {
      const mark = Number(raw.marks[i]);
      if (!Number.isNaN(mark)) {
        subjects.push({ subject: `Subject ${i + 1}`, mark });
      }
    }
  }

  if (subjects.length === 0) return null;
  return createStudentRecord(id, name, subjects);
}

function formatSubjectsInline(r: StudentRecord): string {
  const parts: string[] = [];
  for (let i = 0; i < r.subjects.length; i++) {
    parts.push(`${r.subjects[i].subject}: ${r.subjects[i].mark}`);
  }
  return parts.join(", ");
}

function toFullDetailString(r: StudentRecord): string {
  return `${r.id} | ${r.name} | [${formatSubjectsInline(r)}] | Total: ${r.total}/${r.maxMarks} | Percentage: ${r.percentage.toFixed(2)} | Result: ${r.result}`;
}

function toSortString(r: StudentRecord): string {
  return `${r.id} | ${r.name} | ${r.percentage.toFixed(2)} | ${r.result}`;
}

function computeNextId(list: StudentRecord[]): string {
  if (list.length === 0) return "ERP";
  let maxId: number | null = null;
  for (let i = 0; i < list.length; i++) {
    const digits = list[i].id.replace(/[^0-9]/g, "");
    if (digits) {
      const num = parseInt(digits, 10);
      if (!Number.isNaN(num) && (maxId === null || num > maxId)) {
        maxId = num;
      }
    }
  }
  return maxId !== null ? formatId(maxId + 1) : "ERP";
}

/**
 * Custom Linear Search (no built-in search methods).
 */
function searchById(list: StudentRecord[], query: string): { record: StudentRecord | null; index: number } {
  const target = normalizeIdKey(query);
  if (!target || target === "ERP") return { record: null, index: -1 };
  for (let i = 0; i < list.length; i++) {
    if (normalizeIdKey(list[i].id) === target) {
      return { record: list[i], index: i };
    }
  }
  return { record: null, index: -1 };
}

/**
 * Custom Adaptive Bubble Sort descending by percentage (no built-in sort methods).
 */
function sortByPercentage(list: StudentRecord[]): void {
  const n = list.length;
  if (n <= 1) return;
  for (let i = 0; i < n - 1; i++) {
    let swapped = false;
    for (let j = 0; j < n - i - 1; j++) {
      if (list[j].percentage < list[j + 1].percentage) {
        const temp = list[j];
        list[j] = list[j + 1];
        list[j + 1] = temp;
        swapped = true;
      }
    }
    if (!swapped) break;
  }
}

/**
 * Loads records from Firebase Realtime Database (primary) with Netlify Blobs fallback.
 */
async function loadRecords(context: Context): Promise<StudentRecord[]> {
  const fbUrl = getFirebaseDbUrl();
  if (fbUrl) {
    try {
      const res = await fetch(`${fbUrl}/examResults.json`, {
        method: "GET",
        headers: { Accept: "application/json" },
      });
      if (res.ok) {
        const data = (await res.json()) as { records?: unknown[] } | unknown[] | null;
        if (!data) return [];
        const rawList = Array.isArray(data)
          ? data
          : Array.isArray(data.records)
          ? data.records
          : Object.values(data as Record<string, unknown>);
        const parsed: StudentRecord[] = [];
        for (let i = 0; i < rawList.length; i++) {
          if (rawList[i] && typeof rawList[i] === "object") {
            const norm = normalizeStoredRecord(rawList[i] as Record<string, unknown>);
            if (norm) parsed.push(norm);
          }
        }
        return parsed;
      }
    } catch {
      // Fall through to secondary store if network error
    }
  }

  try {
    const store =
      context.deploy?.context === "production"
        ? getStore({ name: "exam-results", consistency: "strong" })
        : getDeployStore("exam-results");
    const rawJson = await store.get(BLOB_KEY);
    if (!rawJson) return [];
    const arr = JSON.parse(rawJson) as Record<string, unknown>[];
    const parsed: StudentRecord[] = [];
    for (let i = 0; i < arr.length; i++) {
      const norm = normalizeStoredRecord(arr[i]);
      if (norm) parsed.push(norm);
    }
    return parsed;
  } catch {
    return memoryFallbackRecords.slice();
  }
}

/**
 * Persists records to Firebase Realtime Database automatically on every Add / Remove / Sort / Clear.
 */
async function saveRecords(context: Context, list: StudentRecord[]): Promise<void> {
  memoryFallbackRecords = list.slice();
  const fbUrl = getFirebaseDbUrl();

  if (fbUrl) {
    try {
      if (list.length === 0) {
        await fetch(`${fbUrl}/examResults.json`, { method: "DELETE" });
      } else {
        await fetch(`${fbUrl}/examResults.json`, {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            updatedAt: Date.now(),
            nextId: computeNextId(list),
            records: list,
          }),
        });
      }
    } catch {
      // Continue to secondary backup store
    }
  }

  try {
    const store =
      context.deploy?.context === "production"
        ? getStore({ name: "exam-results", consistency: "strong" })
        : getDeployStore("exam-results");
    if (list.length === 0) {
      await store.delete(BLOB_KEY);
    } else {
      await store.set(BLOB_KEY, JSON.stringify(list));
    }
  } catch {
    // Kept in memoryFallbackRecords for unlinked local dev
  }
}

function parseSubjectsPayload(body: Record<string, unknown>): {
  ok: boolean;
  subjects: SubjectEntry[];
  error: string;
} {
  if (Array.isArray(body.subjects)) {
    const rawRows = body.subjects.map((item) => {
      const obj = (item ?? {}) as Record<string, unknown>;
      return {
        subject: sanitizeText(String(obj.subject ?? "")),
        markStr: String(obj.mark ?? "").trim(),
      };
    });

    // Strip trailing completely empty auto-grow rows
    while (
      rawRows.length > 0 &&
      rawRows[rawRows.length - 1].subject === "" &&
      rawRows[rawRows.length - 1].markStr === ""
    ) {
      rawRows.pop();
    }

    if (rawRows.length === 0) {
      return {
        ok: false,
        subjects: [],
        error: "Please enter at least 1 subject name and its marks (0 - 100).",
      };
    }

    const result: SubjectEntry[] = [];
    const seenNames: string[] = [];

    for (let i = 0; i < rawRows.length; i++) {
      const row = rawRows[i];
      if (row.subject === "" && row.markStr === "") {
        continue;
      }
      if (row.subject === "") {
        return {
          ok: false,
          subjects: [],
          error: `Please enter the subject name for row #${i + 1}.`,
        };
      }
      if (row.markStr === "") {
        return {
          ok: false,
          subjects: [],
          error: `Please enter marks (0 - 100) for "${row.subject}".`,
        };
      }

      const lowerKey = row.subject.toLowerCase();
      for (let s = 0; s < seenNames.length; s++) {
        if (seenNames[s] === lowerKey) {
          return {
            ok: false,
            subjects: [],
            error: `Duplicate subject "${row.subject}". Each subject for a student should have a unique name.`,
          };
        }
      }
      seenNames.push(lowerKey);

      const num = Number(row.markStr);
      if (!Number.isInteger(num)) {
        return {
          ok: false,
          subjects: [],
          error: `Invalid marks for "${row.subject}". Each mark must be an integer between 0 and 100.`,
        };
      }
      if (num < 0 || num > 100) {
        return {
          ok: false,
          subjects: [],
          error: "Invalid marks. Each mark must be between 0 and 100.",
        };
      }
      result.push({ subject: row.subject, mark: num });
    }

    if (result.length === 0) {
      return {
        ok: false,
        subjects: [],
        error: "Please enter at least 1 subject name and its marks (0 - 100).",
      };
    }

    return { ok: true, subjects: result, error: "" };
  }

  return {
    ok: false,
    subjects: [],
    error: "Please enter at least 1 subject name and its marks (0 - 100).",
  };
}

function buildPayload(
  list: StudentRecord[],
  success: boolean,
  message: string,
  record: StudentRecord | null,
  matchedIndex: number
) {
  let pass = 0;
  for (let i = 0; i < list.length; i++) {
    if (list[i].result === "Pass") pass++;
  }
  const fbUrl = getFirebaseDbUrl();
  return {
    success,
    message,
    nextId: computeNextId(list),
    matchedIndex,
    record,
    firebaseDatabaseUrl: fbUrl || null,
    stats: {
      total: list.length,
      pass,
      fail: list.length - pass,
    },
    records: list,
  };
}

export default async (req: Request, context: Context) => {
  const list = await loadRecords(context);

  if (req.method === "GET") {
    return Response.json(buildPayload(list, true, "Ready.", null, -1));
  }

  if (req.method === "POST") {
    let body: Record<string, unknown> = {};
    try {
      body = (await req.json()) as Record<string, unknown>;
    } catch {
      return Response.json({ success: false, message: "Invalid JSON request body." }, { status: 400 });
    }

    const action = String(body.action ?? "").toLowerCase();

    if (action === "add") {
      const rawErp = String(body.erpId ?? "").trim();
      let resolvedId: string | null = null;

      if (rawErp && rawErp.toUpperCase() !== "ERP") {
        resolvedId = parseErpInput(rawErp);
        if (!resolvedId) {
          return Response.json(
            buildPayload(list, false, "Invalid ERP ID. Please enter a valid ERP number.", null, -1),
            { status: 400 }
          );
        }
      } else {
        const autoId = computeNextId(list);
        if (autoId === "ERP") {
          return Response.json(
            buildPayload(
              list,
              false,
              "Please enter the first ERP ID so the system can auto-detect subsequent ERP IDs.",
              null,
              -1
            ),
            { status: 400 }
          );
        }
        resolvedId = autoId;
      }

      if (searchById(list, resolvedId).record !== null) {
        return Response.json(
          buildPayload(list, false, `Duplicate ERP ID (${resolvedId}). A record with this ID already exists.`, null, -1),
          { status: 400 }
        );
      }

      const rawName = sanitizeText(String(body.name ?? ""));
      if (!rawName) {
        return Response.json(
          buildPayload(list, false, "Invalid name. Name cannot be empty.", null, -1),
          { status: 400 }
        );
      }

      const parsedSubjects = parseSubjectsPayload(body);
      if (!parsedSubjects.ok) {
        return Response.json(buildPayload(list, false, parsedSubjects.error, null, -1), { status: 400 });
      }

      const created = createStudentRecord(resolvedId, rawName, parsedSubjects.subjects);
      list.push(created);
      await saveRecords(context, list);

      const msg = `Result added. ID: ${toFullDetailString(created)}`;
      return Response.json(buildPayload(list, true, msg, created, list.length - 1));
    }

    if (action === "remove") {
      const targetId = String(body.id ?? "").trim();
      if (!targetId) {
        return Response.json(
          buildPayload(list, false, "Please specify an ERP ID to remove.", null, -1),
          { status: 400 }
        );
      }
      const { record, index } = searchById(list, targetId);
      if (!record || index < 0) {
        return Response.json(
          buildPayload(list, false, `Result not found for ID: ${targetId}`, null, -1),
          { status: 404 }
        );
      }
      list.splice(index, 1);
      await saveRecords(context, list);
      return Response.json(
        buildPayload(list, true, `Removed student record: ${record.id} | ${record.name}`, null, -1)
      );
    }

    if (action === "search") {
      const query = String(body.id ?? "").trim();
      if (!query) {
        return Response.json(
          buildPayload(list, false, "Please enter an ERP ID to search.", null, -1),
          { status: 400 }
        );
      }
      const { record, index } = searchById(list, query);
      if (record) {
        return Response.json(buildPayload(list, true, `Found: ${toFullDetailString(record)}`, record, index));
      }
      return Response.json(buildPayload(list, false, `Result not found for ID: ${query}`, null, -1));
    }

    if (action === "sort") {
      if (list.length === 0) {
        return Response.json(buildPayload(list, false, "No results found.", null, -1));
      }
      sortByPercentage(list);
      await saveRecords(context, list);
      const lines: string[] = [];
      for (let i = 0; i < list.length; i++) {
        lines.push(`${i + 1}. ${toSortString(list[i])}`);
      }
      return Response.json(buildPayload(list, true, lines.join("\n"), list[0], 0));
    }

    if (action === "clear") {
      list.length = 0;
      await saveRecords(context, list);
      return Response.json(
        buildPayload(list, true, "Cleared all saved records. Auto-detected ERP ID reset to ERP.", null, -1)
      );
    }

    return Response.json({ success: false, message: "Unsupported action." }, { status: 400 });
  }

  return new Response("Method not allowed", { status: 405 });
};

export const config: Config = {
  path: "/api/results",
};
