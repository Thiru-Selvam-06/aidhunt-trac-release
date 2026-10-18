#!/usr/bin/env python3
"""Structural reconciliation only. Successful validation is NOT behavioral verification."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
import sys
import struct
import zlib

ALLOWED_STATUSES = {
    "VERIFIED — Automated Test", "VERIFIED — Instrumented/Virtual Device",
    "IMPLEMENTED — Verification Incomplete", "DOCUMENTED — Accepted Limitation",
    "BLOCKED — Authorization Required", "FAILED", "NOT VERIFIED",
}
ID = r"[A-Za-z][A-Za-z0-9_-]*"

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def valid_png(path):
    try:
        data = path.read_bytes()
        if data[:8] != b"\x89PNG\r\n\x1a\n":
            return False
        offset, first = 8, True
        while offset + 12 <= len(data):
            size = struct.unpack(">I", data[offset:offset + 4])[0]
            kind = data[offset + 4:offset + 8]
            end = offset + 8 + size
            if end + 4 > len(data) or (first and (kind != b"IHDR" or size != 13)):
                return False
            if zlib.crc32(data[offset + 4:end]) & 0xffffffff != struct.unpack(">I", data[end:end + 4])[0]:
                return False
            if kind == b"IEND":
                return size == 0 and end + 4 == len(data)
            first, offset = False, end + 4
        return False
    except (OSError, struct.error):
        return False

def inspect(root, audit=None, matrix=None):
    root = Path(root).resolve()
    audit = Path(audit or root / "docs/architecture-compliance-audit.md")
    matrix = Path(matrix or root / "docs/architecture-v16-requirement-matrix.md")
    errors = []
    if not audit.is_file() or not matrix.is_file():
        return {"errors": ["Source audit or matrix file is missing"], "structural_pass": False}
    source_text, text = audit.read_text(encoding="utf-8"), matrix.read_text(encoding="utf-8")
    headings = list(re.finditer(r"^###\s+(" + ID + r")\s+—\s+(.+)$", source_text, re.M))
    source_ids = []
    for i, heading in enumerate(headings):
        source_ids.append(heading.group(1))
        body = source_text[heading.end():headings[i + 1].start() if i + 1 < len(headings) else len(source_text)]
        if not re.search(r"^\*\*Requirement:\*\*\s+\S", body, re.M):
            errors.append(f"Source {heading.group(1)}: missing Requirement field")
    if not source_ids:
        errors.append("Source audit contains no requirement headings")
    source_dupes = sorted(k for k, n in Counter(source_ids).items() if n > 1)
    if source_dupes:
        errors.append(f"Duplicate source IDs: {source_dupes}")
    rows, in_register = [], False
    for line_no, line in enumerate(text.splitlines(), 1):
        stripped = line.strip()
        if re.match(r"^\|\s*ID\s*\|", stripped, re.I):
            in_register = True
            continue
        if not in_register:
            continue
        if stripped.startswith("#"):
            in_register = False
            continue
        if not stripped:
            continue
        if re.fullmatch(r"\|?[\s|:-]+\|?", stripped):
            continue
        if not stripped.startswith("|"):
            # A dropped leading pipe must not silently hide a requirement.
            if re.match(ID + r"\s*\|", stripped):
                errors.append(f"Matrix line {line_no}: malformed requirement row (missing leading pipe)")
            continue
        fields = [v.strip() for v in stripped.split("|")[1:-1]]
        if not stripped.endswith("|") or len(fields) != 6 or not re.fullmatch(ID, fields[0]):
            errors.append(f"Matrix line {line_no}: malformed requirement row; expected six fields")
            continue
        rows.append({"id": fields[0], "line": line_no, "anchor": fields[3], "evidence": fields[4], "status": fields[5]})
    matrix_ids = [r["id"] for r in rows]
    dupes = sorted(k for k, n in Counter(matrix_ids).items() if n > 1)
    missing, extra = sorted(set(source_ids) - set(matrix_ids)), sorted(set(matrix_ids) - set(source_ids))
    for name, values in [("Missing matrix IDs", missing), ("Extra matrix IDs", extra), ("Duplicate matrix IDs", dupes)]:
        if values:
            errors.append(f"{name}: {values}")
    counts = Counter(r["status"] for r in rows)
    for row in rows:
        if row["status"] not in ALLOWED_STATUSES:
            errors.append(f"{row['id']}: invalid status {row['status']!r}")
        for cell in [row["anchor"], row["evidence"]]:
            for target in re.findall(r"\]\(([^)]+)\)", cell):
                if re.match(r"https?://", target):
                    continue  # Availability of remote URLs is outside structural validation.
                if target.startswith("file:") or re.match(r"[A-Za-z]:", target):
                    errors.append(f"{row['id']}: nonportable evidence link {target}")
                    continue
                pathpart = target.split("#", 1)[0]
                line_match = re.search(r":(\d+)$", pathpart)
                if line_match:
                    pathpart = pathpart[:line_match.start()]
                resolved = (matrix.parent / pathpart).resolve()
                if not resolved.is_relative_to(root) or not resolved.is_file():
                    errors.append(f"{row['id']}: missing or outside-repository evidence target {target}")
                elif line_match and not 1 <= int(line_match.group(1)) <= len(resolved.read_text(encoding="utf-8").splitlines()):
                    errors.append(f"{row['id']}: evidence line out of range {target}")
            for image in re.findall(r"`([^`]+\.png)`", cell):
                image_path = root / "docs/test-artifacts/emulator-acceptance-2026-10-17" / image
                if not image_path.is_file():
                    errors.append(f"{row['id']}: missing screenshot {image}")
                elif not valid_png(image_path):
                    errors.append(f"{row['id']}: invalid PNG screenshot {image}")
        if row["status"] == "VERIFIED — Automated Test":
            for cls, method in re.findall(r"`(\w+Test)\.(\w+)`", row["evidence"]):
                files = list((root / "app/src").rglob(cls + ".kt"))
                if not any(re.search(r"\bfun\s+" + re.escape(method) + r"\s*\(", f.read_text(encoding="utf-8")) for f in files):
                    errors.append(f"{row['id']}: nonexistent test assertion {cls}.{method}")
    # Read only the summary before the register. Every category, including zero, is required.
    summary = text.split("## 2.", 1)[0]
    for status in sorted(ALLOWED_STATUSES):
        hits = re.findall(r"^\s*-\s*\*\*" + re.escape(status) + r":\*\*\s*(\d+)\s+items\b", summary, re.M)
        if len(hits) != 1:
            errors.append(f"Summary must declare {status!r} exactly once")
        elif int(hits[0]) != counts[status]:
            errors.append(f"Summary mismatch {status}: reported {hits[0]}, actual {counts[status]}")
    totals = re.findall(r"Total Requirements:\s*\*\*(\d+)\s+items\*\*", summary)
    if len(totals) != 1 or int(totals[0]) != len(source_ids) or len(rows) != len(source_ids):
        errors.append(f"Total mismatch: summary {totals}, source rows {len(source_ids)}, matrix rows {len(rows)}")
    return {"source_count": len(source_ids), "source_unique_count": len(set(source_ids)),
        "matrix_count": len(rows), "matrix_unique_count": len(set(matrix_ids)),
        "missing_ids": missing, "extra_ids": extra, "source_duplicates": source_dupes,
        "matrix_duplicates": dupes, "status_counts": dict(counts),
        "source_sha256": digest(audit), "matrix_sha256": digest(matrix),
        "validator_sha256": digest(Path(__file__)), "errors": errors,
        "structural_pass": not errors, "behavioral_verification": False}

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--json", type=Path)
    args = parser.parse_args()
    result = inspect(args.root)
    output = json.dumps(result, ensure_ascii=False, indent=2)
    print(output)
    if args.json:
        args.json.write_text(output + "\n", encoding="utf-8")
    return 0 if result["structural_pass"] else 1

if __name__ == "__main__":
    sys.exit(main())
