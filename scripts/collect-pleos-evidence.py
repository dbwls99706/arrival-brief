#!/usr/bin/env python3
"""Collect a read-only, privacy-filtered snapshot from an already running device.

This never installs/starts an app, grants permissions, changes a vehicle signal,
or declares a live SDK integration pass. Raw dumps, UI XML, addresses, notices,
parking notes, serial numbers, screenshots and logcat are not written to disk.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

PACKAGE = "studio.ax.arrival"
PERMISSION = "pleos.car.permission.NAVI_ROUTE"
PHASES = ("baseline", "nav-match", "nav-mismatch", "nav-unavailable", "ux-restricted", "ux-restored")
MAX_OUTPUT = 4 * 1024 * 1024
NAVIGATION_LABELS = {
    "차량 내비 목적지와 행사 시설을 대조하세요.": "not_checked",
    "차량 내비 목적지를 확인하고 있습니다.": "checking",
    "차량 내비 정보를 읽지 못했습니다. 연결·권한 또는 목적지 설정을 확인하세요.": "unavailable",
    "내비 목적지의 코엑스 삼성동 시설명과 주소가 일치합니다. 회의실·진입문까지 확인한 결과는 아닙니다.": "matched_label",
    "내비 목적지가 지원하는 코엑스 삼성동 회의시설과 다릅니다. 목적지를 확인한 뒤 다시 대조하세요.": "mismatch_label",
    "내비 목적지의 시설명이 지원하는 코엑스 회의시설과 다릅니다. 목적지를 확인한 뒤 다시 대조하세요.": "mismatch_label",
    "내비 목적지 이름이 없어 행사 시설과 대조할 수 없습니다.": "unknown_label",
    "내비 목적지에 코엑스 이름이 있지만 지원하는 시설 명칭과 대조할 수 없습니다. 행사 시설을 직접 확인하세요.": "unknown_label",
    "코엑스 이름은 있지만 주소만으로 삼성동 회의시설을 확인할 수 없습니다.": "unknown_label",
    "내비 미연결 · 방문 시설을 직접 확인하세요.": "non_automotive_label",
}
PLATFORM_LABELS = {
    "플랫폼 UX 입력 허용 · 실제 정차 여부를 확인하세요.": "input_allowed_label",
    "플랫폼 주행 규제 활성 · 입력 잠금": "restricted_label",
    "플랫폼 상태 미확인 · 입력 잠금": "unknown_label",
    "차량 서비스 연결 끊김 · 입력 잠금": "disconnected_label",
    "플랫폼 주행 규제 조회 불가 · 입력 잠금": "unavailable_label",
    "차량 서비스 응답 없음 · 입력 잠금": "timeout_label",
    "차량 상태 미확인 · 입력 잠금": "unknown_label",
    "차량 상태 다시 확인 중 · 입력 잠금": "unknown_label",
    "플랫폼 주행 규제 연결 중 · 입력 잠금": "connecting_label",
}
ACTION_LABELS = {
    "차량 내비 목적지 대조": "compare_navigation",
    "도착 브리핑 확정": "confirm_brief",
    "브리핑 복사": "copy_brief",
    "안내 수정": "edit_brief",
    "휴대폰으로 가져가기": "show_memo_qr",
}
DEMO_LABELS = {
    "데모 · 정차 · 실제 차량 미연결",
    "데모 · 주행 · 입력 잠금 · 실제 차량 미연결",
    "데모 · 미확인 · 입력 잠금 · 실제 차량 미연결",
}


def parse_package_dump(raw: str) -> dict:
    versions = set(re.findall(r"(?m)^\s*versionCode=(\d+)\b", raw))
    permissions = set(re.findall(r"(?m)^\s*" + re.escape(PERMISSION) + r": granted=(true|false)\b", raw))
    return {
        "version_code": int(next(iter(versions))) if len(versions) == 1 else None,
        "navi_route_granted": next(iter(permissions)) == "true" if len(permissions) == 1 else None,
        "permission_scope": "reported package dump entries; unknown if absent or user entries disagree",
    }


def parse_ux_dump(raw: str) -> dict:
    # Current per-display rows only; never consume historical transition logs.
    rows = []
    for line in raw.splitlines():
        match = re.fullmatch(r"\s*Port: (0x[0-9a-fA-F]+) UXR: DO: (true|false) UxR: (\d+) time: (\d+)\s*", line)
        if match:
            rows.append({"display_port": int(match[1], 16), "requires_distraction_optimization": match[2] == "true", "active_restrictions": int(match[3]), "platform_timestamp_ns": int(match[4])})
    # Avoid accepting an unrelated dump which merely contains a copied row.
    valid = "*CarUxRestrictionsManagerService*" in raw
    return {"format": "aosp_current_port_rows" if valid and rows else "unknown", "current_displays": rows if valid else []}


def parse_ui(raw: str) -> dict:
    result = {"status": "unknown", "app_nodes_visible": False, "navigation_labels_seen": [], "platform_labels_seen": [], "demo_label_seen": False, "visible_actions": {}}
    start, end = raw.find("<hierarchy"), raw.rfind("</hierarchy>")
    if start < 0 or end < start or len(raw) > MAX_OUTPUT:
        return result
    try:
        root = ET.fromstring(raw[start:end + len("</hierarchy>")])
    except ET.ParseError:
        return result
    nodes = [node for node in root.iter("node") if node.get("package") == PACKAGE]
    result["status"] = "parsed" if nodes else "app_not_visible"
    result["app_nodes_visible"] = bool(nodes)
    for node in nodes:
        label, kind = node.get("text", ""), node.get("class", "")
        if kind == "android.widget.TextView":
            if label in NAVIGATION_LABELS:
                result["navigation_labels_seen"].append(NAVIGATION_LABELS[label])
            if label in PLATFORM_LABELS:
                result["platform_labels_seen"].append(PLATFORM_LABELS[label])
            if label in DEMO_LABELS:
                result["demo_label_seen"] = True
        elif kind == "android.widget.Button" and label in ACTION_LABELS:
            value = node.get("enabled")
            result["visible_actions"].setdefault(ACTION_LABELS[label], []).append(value == "true" if value in ("true", "false") else None)
    result["navigation_labels_seen"] = sorted(set(result["navigation_labels_seen"]))
    result["platform_labels_seen"] = sorted(set(result["platform_labels_seen"]))
    return result


class AdbReader:
    def __init__(self, adb: str, serial: str, timeout: float):
        self.adb, self.serial, self.timeout = adb, serial, timeout
        self.commands = []

    def read(self, label: str, args: list[str]) -> str | None:
        start = time.monotonic()
        status, code, data = "unknown", None, None
        try:
            run = subprocess.run([self.adb, "-s", self.serial, *args], stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=self.timeout, check=False)
            code = run.returncode
            status = "ok" if code == 0 else "command_failed"
            if code == 0 and len(run.stdout) <= MAX_OUTPUT:
                data = run.stdout.decode("utf-8", errors="replace")
            elif len(run.stdout) > MAX_OUTPUT:
                status = "output_too_large"
        except subprocess.TimeoutExpired:
            status = "timeout"
        except OSError:
            status = "adb_unavailable"
        self.commands.append({"label": label, "status": status, "exit_code": code, "elapsed_seconds": round(time.monotonic() - start, 3)})
        return data


def collect(reader: AdbReader, release_apk: Path, phase: str, include_ui: bool) -> dict:
    expected = hashlib.sha256(release_apk.read_bytes()).hexdigest()
    result = {
        "schema": 1,
        "captured_utc": datetime.now(timezone.utc).isoformat(),
        "requested_phase_label": phase,
        "scope": "Read-only device snapshot; phase name and UI labels are not SDK callback proof or an integration pass",
        "integration_verified": False,
        "release_sha256": expected,
        "device_tag_sha256": hashlib.sha256(reader.serial.encode()).hexdigest(),
        "privacy": "Raw serial, raw device dumps, UI XML, notices, addresses, memo text, screenshots and logcat are not persisted",
        "commands": reader.commands,
    }
    state = reader.read("adb_state", ["get-state"])
    result["adb_connected"] = state.strip() == "device" if state is not None else None
    if result["adb_connected"] is not True:
        result["collection_status"] = "device_unavailable"
        return result
    boot = reader.read("boot_completed", ["shell", "getprop", "sys.boot_completed"])
    sdk = reader.read("android_api", ["shell", "getprop", "ro.build.version.sdk"])
    emulator = reader.read("emulator_flag", ["shell", "getprop", "ro.kernel.qemu"])
    features = reader.read("automotive_feature", ["shell", "pm", "list", "features"])
    result["device"] = {
        "boot_completed": boot.strip() == "1" if boot is not None and boot.strip() in ("0", "1") else None,
        "android_api": int(sdk.strip()) if sdk is not None and re.fullmatch(r"\d{1,3}", sdk.strip()) else None,
        "emulator": emulator.strip() == "1" if emulator is not None and emulator.strip() in ("0", "1") else None,
        "automotive_feature": "feature:android.hardware.type.automotive" in features.splitlines() if features is not None and "feature:" in features else None,
        "pleos_identity_verified": False,
    }
    package = reader.read("package_metadata", ["shell", "dumpsys", "package", PACKAGE])
    result["app"] = parse_package_dump(package or "")
    paths = reader.read("installed_apk_paths", ["shell", "pm", "path", PACKAGE])
    # The shell transport must never receive arbitrary device-provided metacharacters.
    apk_paths = re.findall(r"(?m)^package:((?:/data/app|/system|/product|/vendor)/[-A-Za-z0-9_/.=+]+\.apk)\r?$", paths or "")
    base_paths = [path for path in apk_paths if path.endswith("/base.apk")]
    selected = base_paths[0] if len(base_paths) == 1 else apk_paths[0] if len(apk_paths) == 1 else None
    installed_hash = None
    if selected:
        digest = reader.read("installed_apk_sha256", ["shell", "sha256sum", selected])
        match = re.fullmatch(r"([0-9a-fA-F]{64})\s+[^\r\n]+\s*", digest or "")
        if match:
            installed_hash = match[1].lower()
    result["app"].update({"installed_sha256": installed_hash, "release_hash_matches": installed_hash == expected if installed_hash else None})
    ux = reader.read("current_ux_restrictions", ["shell", "dumpsys", "car_service", "--services", "CarUxRestrictionsManagerService"])
    result["ux_restrictions"] = parse_ux_dump(ux or "")
    if include_ui:
        ui = reader.read("ui_fixed_labels", ["shell", "-tt", "uiautomator", "dump", "/dev/tty"])
        result["ui"] = parse_ui(ui or "")
    else:
        result["ui"] = {"status": "not_requested"}
    result["collection_status"] = "snapshot_collected" if all(c["status"] == "ok" for c in reader.commands) else "partial_snapshot"
    result["interpretation"] = "Compare separate snapshots with independently recorded scenario actions; unknown/hidden UI is not a failure or a successful callback. OS driving restrictions may hide this non-DO activity entirely."
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--release-apk", type=Path, required=True)
    parser.add_argument("--phase", choices=PHASES, default="baseline")
    parser.add_argument("--include-ui", action="store_true", help="Read accessibility XML transiently; save only fixed labels and selected button enabled flags")
    parser.add_argument("--timeout", type=float, default=15)
    parser.add_argument("--output", type=Path, required=True, help="New JSON file; an existing evidence file is never overwritten")
    args = parser.parse_args()
    if not args.release_apk.is_file() or not 0 < args.timeout <= 60:
        parser.error("An existing APK and timeout in (0, 60] are required")
    if args.output.exists():
        parser.error("Output already exists; use a new filename for each observation")
    reader = AdbReader(args.adb, args.serial, args.timeout)
    report = collect(reader, args.release_apk, args.phase, args.include_ui)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("x", encoding="utf-8") as output:
        json.dump(report, output, ensure_ascii=False, indent=2)
        output.write("\n")
    print(f"{report['collection_status']}; integration remains unverified")
    return 0 if report["collection_status"] == "snapshot_collected" else 1


if __name__ == "__main__":
    raise SystemExit(main())
