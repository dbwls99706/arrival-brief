#!/usr/bin/env python3
"""Decode original QR screenshots with two readers and an independent save-time window.

Requires the dependencies of verify-qr-handoff.py and the pinned jsQR bundle.
Records unreadable captures without editing their pixels. This is not a phone scan.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import types
from zoneinfo import ZoneInfo

ROOT = Path(__file__).resolve().parents[1]
DECODERS = ("opencv", "jsqr")


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def local_path(value):
    path = Path(value)
    if path.is_absolute() or ".." in path.parts:
        raise ValueError("Evidence paths must be relative to the repository")
    if len(path.parts) == 1:
        path = Path("docs/verification") / path
    return ROOT / path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--runtime", type=Path, required=True)
    parser.add_argument("--jsqr", type=Path, required=True)
    parser.add_argument("--node", default="node")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--require-all", action="store_true", help="Return failure after saving the report if either reader cannot decode any capture")
    args = parser.parse_args()
    runtime = json.loads(args.runtime.read_text())
    spec = importlib.util.spec_from_file_location("qr_readers", Path(__file__).with_name("verify-qr-handoff.py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    if sha(args.jsqr) != module.JSQR_SHA256:
        raise ValueError("jsQR bundle differs from the pinned upstream bytes")
    module.cv2.setNumThreads(1)
    oracles = {}
    for oracle in runtime["qr_oracles"]:
        key = (oracle["build_type"], float(oracle["font_scale"]))
        if key in oracles:
            raise ValueError("Duplicate save-time oracle")
        source = oracle["source"]
        if sha(local_path(source["path"])) != source["sha256"]:
            raise ValueError("Save-time oracle file changed")
        window = oracle["saved_at_window_ms"]
        if not (isinstance(window["before"], int) and isinstance(window["after"], int) and 0 < window["before"] <= window["after"]):
            raise ValueError("Invalid independently recorded save-time window")
        oracles[key] = oracle
    required = {(b, f, m) for b in ("debug", "release") for f in (1.0, 1.3) for m in ("standard", "expanded")}
    seen, rows = set(), []
    readers = module.Decoders(types.SimpleNamespace(node=args.node, jsqr=str(args.jsqr)))
    try:
        for shot in runtime["screenshots"]:
            if shot.get("display_mode") not in ("standard", "expanded"):
                continue
            build, font, mode = shot["build_type"], float(shot["font_scale"]), shot["display_mode"]
            key = build, font, mode
            if key not in required or key in seen:
                raise ValueError("Unexpected or repeated QR capture combination")
            seen.add(key)
            path = local_path(shot["path"])
            if sha(path) != shot["sha256"]:
                raise ValueError("Screenshot bytes changed")
            expected_apk = runtime["release_apk_sha256"] if build == "release" else runtime["aosp"]["debug_apk_sha256"]
            if shot["apk_sha256"] != expected_apk:
                raise ValueError("Screenshot refers to a different APK")
            oracle = oracles[(build, font)]
            window, memo = oracle["saved_at_window_ms"], oracle["expected_memo"]
            pattern = re.compile(r"Arrival Brief\n주차 메모: " + re.escape(memo) + r"\n(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}) KST\n휴대폰 복사본은 자동 삭제되지 않음\.")
            decoded = readers.decode(module.Image.open(path))
            matches = {}
            for name in DECODERS:
                match = pattern.fullmatch(decoded[name] or "")
                stamp = None
                if match:
                    try:
                        stamp = int(datetime.strptime(match.group(1), "%Y-%m-%d %H:%M:%S").replace(tzinfo=ZoneInfo("Asia/Seoul")).timestamp() * 1000)
                    except ValueError:
                        pass
                matches[name] = stamp is not None and window["before"] // 1000 * 1000 <= stamp <= window["after"] // 1000 * 1000
            rows.append({"label": f"{build}_{font:g}_{mode}", "path": path.relative_to(ROOT).as_posix(), "sha256": shot["sha256"],
                         "build_type": build, "apk_sha256": expected_apk, "font_scale": font, "display_mode": mode,
                         "expected_memo": memo, "saved_at_window_ms": window, "oracle_source": oracle["source"],
                         "decoded": decoded, "matches_current_payload": matches,
                         "both_decoders_agree": bool(decoded["opencv"]) and decoded["opencv"] == decoded["jsqr"],
                         "incorrect_nonempty": {name: bool(decoded[name]) and not matches[name] for name in DECODERS}})
    finally:
        readers.close()
    if seen != required:
        raise ValueError("Expected eight original captures: both builds, font scales and display modes")
    debug = [r for r in rows if r["build_type"] == "debug"]
    release = [r for r in rows if r["build_type"] == "release"]
    passed = lambda group: sum(sum(r["matches_current_payload"].values()) for r in group)
    summary = {"standard_large_comparisons": len(debug)*2, "standard_large_pass": passed(debug),
               "release_comparisons": len(release)*2, "release_pass": passed(release),
               "all_comparisons": len(rows)*2, "all_pass": passed(rows),
               "incorrect_nonempty_decodes": sum(sum(r["incorrect_nonempty"].values()) for r in rows)}
    report = {"schema_version": 2, "version": runtime["version"], "checked_utc": datetime.now(timezone.utc).isoformat(),
              "scope": "Two software readers on original full-screen PNG bytes; no cropping, resizing, padding or physical phone camera",
              "release_apk_sha256": runtime["release_apk_sha256"], "runtime_sha256": sha(args.runtime),
              "source_qr_sha256": sha(ROOT / "android/app/src/main/java/studio/ax/arrival/ParkingMemoQr.kt"),
              "tool_script_sha256": sha(Path(__file__)), "jsqr_bundle_sha256": sha(args.jsqr),
              "tool_versions": {"opencv": module.cv2.__version__, "jsqr": "1.4.0"}, "screenshots": rows, "summary": summary,
              "limitations": ["A success on these recorded pixels is not physical phone-camera validation.",
                              "Enlargement preserves the payload and offers another size; it is not a universal decoder fix.",
                              "Earlier RC3 5/6 captures and synthetic size probes remain separate historical observations."]}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(summary))
    return int(bool(summary["incorrect_nonempty_decodes"]) or (args.require_all and summary["all_pass"] != summary["all_comparisons"]))


if __name__ == "__main__":
    raise SystemExit(main())
