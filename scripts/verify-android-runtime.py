#!/usr/bin/env python3
"""Record release launch evidence and optional app-owned instrumentation on an emulator.

The release APK is tested separately from the debug APK. Supplying debug/test APKs
resets ArrivalBrief's test data; this is allowed only on an Android emulator.
This script does not operate system UI or simulate a real Pleos service response.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import os
import re
from pathlib import Path
import subprocess
import time

PACKAGE = "studio.ax.arrival"
RUNNER = PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner"
TEST_CLASS = PACKAGE + ".VisitFlowInstrumentedTest"
LARGE_FONT_METHODS = (
    "savedMemoQrRoundTripsAndClearsWhenEditedOrDriving",
    "realKeyboardKeepsMemoInputVisibleAndAllowsSaving",
    "destinationPickerPreservesConflictingNoticeAndCannotBypassReview",
    "parkingMemoSurvivesRecreationButRemainsLockedUntilReconfirmed",
    "conflictingSourcesShowBothValuesAndPreventConfirmation",
    "confirmedBriefStaysVisibleButDrivingHidesEditingAndCopy",
)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--release-apk", type=Path, required=True)
    parser.add_argument("--baseline-apk", type=Path, help="Optional older APK signed by the same key, to test an update")
    parser.add_argument("--debug-apk", type=Path)
    parser.add_argument("--test-apk", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--large-font-all", action="store_true", help="Repeat all tests at font scale 1.3 instead of the six visual/input cases")
    parser.add_argument("--expected-ui-tests", type=int, default=13, help="Expected full-suite test count (this release: 13)")
    args = parser.parse_args()
    if bool(args.debug_apk) != bool(args.test_apk):
        parser.error("--debug-apk and --test-apk must be supplied together")
    for path in [args.release_apk, args.baseline_apk, args.debug_apk, args.test_apk]:
        if path and not path.is_file():
            parser.error(f"Missing APK: {path}")
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=True)
    observations: dict = {"started_utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()), "serial": args.serial, "commands": []}

    def run(label: str, command: list[str], timeout: int = 120, binary: bool = False, required: bool = True) -> bytes:
        start = time.monotonic()
        argv = [args.adb, "-s", args.serial, *command]
        try:
            result = subprocess.run(argv, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=timeout)
            stdout, stderr, code = result.stdout, result.stderr, result.returncode
        except subprocess.TimeoutExpired as error:
            stdout, stderr, code = error.stdout or b"", error.stderr or b"", 124
        (out / (label + (".png" if binary else ".txt"))).write_bytes(stdout)
        if stderr:
            (out / (label + ".stderr.txt")).write_bytes(stderr)
        observations["commands"].append({"label": label, "arguments": command, "exit": code, "elapsed_seconds": round(time.monotonic() - start, 2)})
        (out / "runtime-result.json").write_text(json.dumps(observations, ensure_ascii=False, indent=2) + "\n")
        print(f"{label}: exit {code}", flush=True)
        if required and code:
            raise RuntimeError(f"{label} failed; see its saved output")
        return stdout

    def install(label: str, apk: Path) -> None:
        data = run(label, ["install", "-r", str(apk.resolve())], timeout=300)
        if b"Success" not in data:
            raise RuntimeError(f"{label} did not report Success")

    try:
        observations["release"] = {"sha256": hashlib.sha256(args.release_apk.read_bytes()).hexdigest(), "bytes": args.release_apk.stat().st_size}
        run("00-device", ["shell", "getprop"])
        if args.baseline_apk:
            install("01-baseline-install", args.baseline_apk)
            run("02-baseline-version", ["shell", "dumpsys", "package", PACKAGE])
        install("03-release-install", args.release_apk)
        run("04-release-version", ["shell", "dumpsys", "package", PACKAGE])
        run("05-release-logcat-clear", ["logcat", "-c"])
        run("06-release-force-stop", ["shell", "am", "force-stop", PACKAGE])
        launch = run("07-release-launch", ["shell", "am", "start", "-W", "-n", PACKAGE + "/.MainActivity"])
        if b"Status: ok" not in launch:
            raise RuntimeError("Release activity did not report Status: ok")
        run("08-release-process", ["shell", "pidof", PACKAGE])
        run("09-release-screen", ["exec-out", "screencap", "-p"], binary=True)
        run("10-release-logcat", ["logcat", "-d"])
        installed_hash = run("10a-release-device-sha", ["shell", "sha256sum $(pm path " + PACKAGE + " | cut -d: -f2)"]).decode().split()[0]
        if installed_hash != observations["release"]["sha256"]:
            raise RuntimeError("Installed release APK SHA-256 does not match the supplied APK")
        observations["release"]["installed_sha256"] = installed_hash
        observations["release"]["install_and_launch"] = "passed"
        if args.debug_apk:
            emulator = run("11-emulator-check", ["shell", "getprop", "ro.kernel.qemu"]).strip()
            if emulator != b"1":
                raise RuntimeError("Refusing to remove app data on a non-emulator device")
            observations["debug"] = {"sha256": hashlib.sha256(args.debug_apk.read_bytes()).hexdigest(), "test_sha256": hashlib.sha256(args.test_apk.read_bytes()).hexdigest(), "scope": "App-owned instrumentation; not proof of Pleos SDK or real vehicle behavior"}
            run("12-release-remove-for-debug", ["uninstall", PACKAGE])
            install("13-debug-install", args.debug_apk)
            install("14-test-install", args.test_apk)
            for label, package, expected in (("14a-debug-device-sha", PACKAGE, observations["debug"]["sha256"]), ("14b-test-device-sha", PACKAGE + ".test", observations["debug"]["test_sha256"])):
                installed_hash = run(label, ["shell", "sha256sum $(pm path " + package + " | cut -d: -f2)"]).decode().split()[0]
                if installed_hash != expected:
                    raise RuntimeError(f"{label} did not match the supplied APK")
            original_scale = run("15-original-font-scale", ["shell", "settings", "get", "system", "font_scale"]).decode().strip()
            original_ime = run("16-original-ime-policy", ["shell", "settings", "get", "secure", "show_ime_with_hard_keyboard"]).decode().strip()
            run("17-enable-test-ime", ["shell", "settings", "put", "secure", "show_ime_with_hard_keyboard", "1"])
            try:
                for scale in ("1.0", "1.3"):
                    tag = "font-" + scale.replace(".", "_")
                    run(tag + "-set", ["shell", "settings", "put", "system", "font_scale", scale])
                    run(tag + "-logcat-clear", ["logcat", "-c"])
                    run(tag + "-clear-old-screens", ["shell", "rm -f /sdcard/Android/data/" + PACKAGE + "/files/arrival-*.png"])
                    test_filter = TEST_CLASS if scale == "1.0" or args.large_font_all else ",".join(TEST_CLASS + "#" + name for name in LARGE_FONT_METHODS)
                    result = run(tag + "-instrumentation", ["shell", "am", "instrument", "-w", "-r", "-e", "class", test_filter, RUNNER], timeout=1200, required=False)
                    run(tag + "-screens", ["pull", "/sdcard/Android/data/" + PACKAGE + "/files", str(out / tag)], required=False)
                    run(tag + "-logcat", ["logcat", "-d"])
                    expected = args.expected_ui_tests if scale == "1.0" or args.large_font_all else len(LARGE_FONT_METHODS)
                    passed = re.search(rb"OK \((\d+) tests?\)", result)
                    observations["debug"][tag] = "passed" if passed and int(passed.group(1)) == expected and b"FAILURES!!!" not in result else "failed_or_incomplete"
            finally:
                if original_ime == "null":
                    run("ime-policy-restore", ["shell", "settings", "delete", "secure", "show_ime_with_hard_keyboard"], required=False)
                else:
                    run("ime-policy-restore", ["shell", "settings", "put", "secure", "show_ime_with_hard_keyboard", original_ime], required=False)
                if original_scale == "null":
                    run("font-scale-restore", ["shell", "settings", "delete", "system", "font_scale"], required=False)
                else:
                    run("font-scale-restore", ["shell", "settings", "put", "system", "font_scale", original_scale], required=False)
        observations["completed_utc"] = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
        return 0 if all(value != "failed_or_incomplete" for value in observations.get("debug", {}).values()) else 1
    except Exception as error:
        observations["error"] = str(error)
        print(str(error), flush=True)
        return 1
    finally:
        (out / "runtime-result.json").write_text(json.dumps(observations, ensure_ascii=False, indent=2) + "\n")


if __name__ == "__main__":
    raise SystemExit(main())
