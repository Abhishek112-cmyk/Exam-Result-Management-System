import type { Config, Context } from "@netlify/functions";
import { getDeployStore, getStore } from "@netlify/blobs";

interface StudentRecord {
  id: string;
  name: string;
  marks: number[];
  total: number;
  maxMarks: number;
  percentage: number;
  result: "Pass" | "Fail";
}

const BLOB_KEY = "results.txt";
let memoryFallbackTxt = "";

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

function createStudentRecord(id: string, name: string, marks: number[]): StudentRecord {
  let total = 0;
  for (let i = 0; i < marks.length; i++) {
    total += marks[i];
  }
  const maxMarks = marks.length * 100;
  const rawPct = maxMarks > 0 ? (total * 100.0) / maxMarks : 0;
  const result: "Pass" | "Fail" = rawPct >= 40.0 ? "Pass" : "Fail";
  return {
    id,
    name,
    marks,
    total,
    maxMarks,
    percentage: Number(rawPct.toFixed(2)),
    result,
  };
}

function toFullDetailString(r: StudentRecord): string {
  return `${r.id} | ${r.name} | Total: ${r.total}/${r.maxMarks} | Percentage: ${r.percentage.toFixed(2)} | Result: ${r.result}`;
}

function toSortString(r: StudentRecord): string {
  return `${r.id} | ${r.name} | ${r.percentage.toFixed(2)} | ${r.result}`;
}

function toFileString(r: StudentRecord): string {
  return `${r.id},${r.name},${r.marks.join(",")},${r.total},${r.percentage.toFixed(2)},${r.result}`;
}

function parseResultsTxt(txt: string): StudentRecord[] {
  const list: StudentRecord[] = [];
  if (!txt) return list;
  const lines = txt.split(/\r?\n/);
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i].trim();
    if (!line || line.startsWith("#")) continue;
    const parts = line.split(",").map((s) => s.trim());
    if (parts.length < 3) continue;

    const parsedId = parseErpInput(parts[0]) ?? parts[0];
    const name = parts[1];
    const lastToken = parts[parts.length - 1].toLowerCase();

    let markEndExclusive = parts.length;
    if ((lastToken === "pass" || lastToken === "fail") && parts.length >= 6) {
      markEndExclusive = parts.length - 3;
    }

    const marks: number[] = [];
    let valid = true;
    for (let j = 2; j < markEndExclusive; j++) {
      const m = parseInt(parts[j], 10);
      if (Number.isNaN(m)) {
        valid = false;
        break;
      }
      marks.push(m);
    }

    if (valid && marks.length >= 1) {
      list.push(createStudentRecord(parsedId, name, marks));
    }
  }
  return list;
}

function serializeResultsTxt(list: StudentRecord[]): string {
  const lines: string[] = [];
  for (let i = 0; i < list.length; i++) {
    lines.push(toFileString(list[i]));
  }
  return lines.join("\n");
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

async function loadRecords(context: Context): Promise<StudentRecord[]> {
  try {
    const store =
      context.deploy?.context === "production"
        ? getStore({ name: "exam-results", consistency: "strong" })
        : getDeployStore("exam-results");
    const txt = await store.get(BLOB_KEY);
    return parseResultsTxt(txt ?? "");
  } catch {
    return parseResultsTxt(memoryFallbackTxt);
  }
}

async function saveRecords(context: Context, list: StudentRecord[]): Promise<void> {
  const txt = serializeResultsTxt(list);
  memoryFallbackTxt = txt;
  try {
    const store =
      context.deploy?.context === "production"
        ? getStore({ name: "exam-results", consistency: "strong" })
        : getDeployStore("exam-results");
    if (list.length === 0) {
      await store.delete(BLOB_KEY);
    } else {
      await store.set(BLOB_KEY, txt);
    }
  } catch {
    // Fallback already stored in memoryFallbackTxt for local unlinked dev
  }
}

function parseMarksPayload(body: Record<string, unknown>): { ok: boolean; marks: number[]; error: string } {
  let rawEntries: string[] = [];

  if (Array.isArray(body.marks)) {
    rawEntries = body.marks.map((v) => String(v ?? "").trim());
  } else if (typeof body.marks === "string") {
    rawEntries = body.marks
      .trim()
      .split(/[,\s]+/)
      .filter((s) => s.length > 0);
  } else if (body.m1 !== undefined || body.m2 !== undefined || body.m3 !== undefined) {
    rawEntries = [String(body.m1 ?? "").trim(), String(body.m2 ?? "").trim(), String(body.m3 ?? "").trim()];
  }

  // Trim trailing empty slots (from the auto-growing blank input at the end)
  while (rawEntries.length > 0 && rawEntries[rawEntries.length - 1] === "") {
    rawEntries.pop();
  }

  if (rawEntries.length === 0) {
    return { ok: false, marks: [], error: "Invalid marks. Please enter marks for at least 1 subject." };
  }

  // Expand any space/comma-separated entries and reject empty gaps between filled subjects
  const tokens: string[] = [];
  for (let i = 0; i < rawEntries.length; i++) {
    if (rawEntries[i] === "") {
      return {
        ok: false,
        marks: [],
        error: `Please enter marks for Subject ${i + 1} or clear empty gaps between subjects.`,
      };
    }
    const split = rawEntries[i].split(/[,\s]+/).filter((s) => s.length > 0);
    for (let k = 0; k < split.length; k++) {
      tokens.push(split[k]);
    }
  }

  const parsed: number[] = [];
  for (let i = 0; i < tokens.length; i++) {
    const num = Number(tokens[i]);
    if (!Number.isInteger(num)) {
      return { ok: false, marks: [], error: "Invalid marks. Each mark must be an integer between 0 and 100." };
    }
    if (num < 0 || num > 100) {
      return { ok: false, marks: [], error: "Invalid marks. Each mark must be between 0 and 100." };
    }
    parsed.push(num);
  }

  return { ok: true, marks: parsed, error: "" };
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
  return {
    success,
    message,
    nextId: computeNextId(list),
    matchedIndex,
    record,
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

      const rawName = String(body.name ?? "")
        .replace(/[,|]/g, " ")
        .replace(/\s+/g, " ")
        .trim();
      if (!rawName) {
        return Response.json(
          buildPayload(list, false, "Invalid name. Name cannot be empty.", null, -1),
          { status: 400 }
        );
      }

      const parsedMarks = parseMarksPayload(body);
      if (!parsedMarks.ok) {
        return Response.json(buildPayload(list, false, parsedMarks.error, null, -1), { status: 400 });
      }

      const created = createStudentRecord(resolvedId, rawName, parsedMarks.marks);
      list.push(created);
      await saveRecords(context, list);

      const msg = `Result added. ID: ${toFullDetailString(created)}`;
      return Response.json(buildPayload(list, true, msg, created, list.length - 1));
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
