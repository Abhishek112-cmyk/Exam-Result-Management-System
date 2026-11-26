import type { Config, Context } from "@netlify/functions";
import { getDeployStore, getStore } from "@netlify/blobs";

interface StudentRecord {
  id: string;
  name: string;
  mark1: number;
  mark2: number;
  mark3: number;
  total: number;
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

function createStudentRecord(id: string, name: string, mark1: number, mark2: number, mark3: number): StudentRecord {
  const total = mark1 + mark2 + mark3;
  const rawPct = (total * 100.0) / 300.0;
  const result: "Pass" | "Fail" = rawPct >= 40.0 ? "Pass" : "Fail";
  return { id, name, mark1, mark2, mark3, total, percentage: Number(rawPct.toFixed(2)), result };
}

function toFullDetailString(r: StudentRecord): string {
  return `${r.id} | ${r.name} | Total: ${r.total}/300 | Percentage: ${r.percentage.toFixed(2)} | Result: ${r.result}`;
}

function toSortString(r: StudentRecord): string {
  return `${r.id} | ${r.name} | ${r.percentage.toFixed(2)} | ${r.result}`;
}

function toFileString(r: StudentRecord): string {
  return `${r.id},${r.name},${r.mark1},${r.mark2},${r.mark3},${r.total},${r.percentage.toFixed(2)},${r.result}`;
}

function parseResultsTxt(txt: string): StudentRecord[] {
  const list: StudentRecord[] = [];
  if (!txt) return list;
  const lines = txt.split(/\r?\n/);
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i].trim();
    if (!line || line.startsWith("#")) continue;
    const parts = line.split(",").map((s) => s.trim());
    if (parts.length >= 5) {
      const parsedId = parseErpInput(parts[0]) ?? parts[0];
      const name = parts[1];
      const m1 = parseInt(parts[2], 10);
      const m2 = parseInt(parts[3], 10);
      const m3 = parseInt(parts[4], 10);
      if (!Number.isNaN(m1) && !Number.isNaN(m2) && !Number.isNaN(m3)) {
        list.push(createStudentRecord(parsedId, name, m1, m2, m3));
      }
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

/**
 * Auto-detects the next sequential ERP ID from existing records.
 * Returns "ERP" (with no hardcoded number) when 0 records exist.
 */
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

      const p1 = String(body.m1 ?? "").trim();
      const p2 = String(body.m2 ?? "").trim();
      const p3 = String(body.m3 ?? "").trim();

      let m1: number, m2: number, m3: number;
      if (p1 && !p2 && !p3 && /[,\s]+/.test(p1)) {
        const tokens = p1.split(/[,\s]+/);
        if (tokens.length !== 3) {
          return Response.json(
            buildPayload(list, false, "Invalid marks. Please enter exactly 3 marks.", null, -1),
            { status: 400 }
          );
        }
        m1 = Number(tokens[0]);
        m2 = Number(tokens[1]);
        m3 = Number(tokens[2]);
      } else {
        if (!p1 || !p2 || !p3) {
          return Response.json(
            buildPayload(list, false, "Invalid marks. Please enter marks for all 3 subjects.", null, -1),
            { status: 400 }
          );
        }
        m1 = Number(p1);
        m2 = Number(p2);
        m3 = Number(p3);
      }

      if (!Number.isInteger(m1) || !Number.isInteger(m2) || !Number.isInteger(m3)) {
        return Response.json(
          buildPayload(list, false, "Invalid marks. Each mark must be an integer between 0 and 100.", null, -1),
          { status: 400 }
        );
      }

      if (m1 < 0 || m1 > 100 || m2 < 0 || m2 > 100 || m3 < 0 || m3 > 100) {
        return Response.json(
          buildPayload(list, false, "Invalid marks. Each mark must be between 0 and 100.", null, -1),
          { status: 400 }
        );
      }

      const created = createStudentRecord(resolvedId, rawName, m1, m2, m3);
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
