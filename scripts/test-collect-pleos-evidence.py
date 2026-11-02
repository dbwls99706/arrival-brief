#!/usr/bin/env python3
"""Synthetic collector tests. These do not exercise Pleos or a live callback."""
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

SCRIPT = Path(__file__).with_name("collect-pleos-evidence.py")
spec = importlib.util.spec_from_file_location("collector", SCRIPT)
collector = importlib.util.module_from_spec(spec)
spec.loader.exec_module(collector)
SECRET = "SYNTHETIC_PRIVATE_NOTICE_ADDRESS_MEMO"


class CollectorTest(unittest.TestCase):
    def test_permission_absence_and_user_disagreement_are_unknown(self):
        permission = collector.PERMISSION
        self.assertIsNone(collector.parse_package_dump(permission)["navi_route_granted"])
        self.assertIsNone(collector.parse_package_dump(f"{permission}: granted=true\n{permission}: granted=false")["navi_route_granted"])
        self.assertFalse(collector.parse_package_dump(f"{permission}: granted=false")["navi_route_granted"])

    def test_historical_restriction_rows_do_not_become_current(self):
        current = "*CarUxRestrictionsManagerService*\nPort: 0x00 UXR: DO: true UxR: 511 time: 12345\n"
        raw = current + "History: DO: false UxR: 0 time: 123\n" + SECRET
        parsed = collector.parse_ux_dump(raw)
        self.assertEqual(parsed["current_displays"], [{"display_port": 0, "requires_distraction_optimization": True, "active_restrictions": 511, "platform_timestamp_ns": 12345}])
        self.assertEqual(collector.parse_ux_dump(current.splitlines()[1])["format"], "unknown")

    def test_ui_does_not_store_free_text_or_accept_other_apps_or_editors(self):
        known = next(label for label, value in collector.NAVIGATION_LABELS.items() if value == "matched_label")
        raw = f'<hierarchy><node package="{collector.PACKAGE}" class="android.widget.EditText" text="{known}"/><node package="other.app" class="android.widget.TextView" text="{known}"/><node package="{collector.PACKAGE}" class="android.widget.TextView" text="{SECRET}"/><node package="{collector.PACKAGE}" class="android.widget.Button" text="휴대폰으로 가져가기" enabled="false"/></hierarchy>'
        parsed = collector.parse_ui(raw)
        self.assertEqual(parsed["navigation_labels_seen"], [])
        self.assertEqual(parsed["visible_actions"], {"show_memo_qr": [False]})
        self.assertNotIn(SECRET, json.dumps(parsed))
        self.assertEqual(collector.parse_ui("ERROR no idle state")["status"], "unknown")
        self.assertEqual(collector.parse_ui('<hierarchy><node package="other.app"/></hierarchy>')["status"], "app_not_visible")

    def run_fake_adb(self, fixture, phase="baseline", include_ui=True):
        with tempfile.TemporaryDirectory(prefix="pleos-synthetic-") as tmp:
            root = Path(tmp)
            apk = root / "synthetic.apk"
            apk.write_bytes(b"synthetic APK fixture, not an Android package")
            digest = hashlib.sha256(apk.read_bytes()).hexdigest()
            mapping = {key: value.replace("EXPECTED_DIGEST", digest) for key, value in fixture.items()}
            mapping_path = root / "fixture.json"
            mapping_path.write_text(json.dumps(mapping))
            fake = root / "fake-adb"
            fake.write_text("#!" + sys.executable + "\nimport json, os, sys\nfrom pathlib import Path\nassert sys.argv[1:3] == ['-s', 'SYNTHETIC_SERIAL_PRIVATE']\nkey = ' '.join(sys.argv[3:])\nmapping = json.loads(Path(os.environ['SYNTHETIC_ADB_FIXTURE']).read_text())\nif key not in mapping:\n print('unexpected command', file=sys.stderr); sys.exit(79)\nprint(mapping[key])\n")
            fake.chmod(0o700)
            output = root / "result.json"
            command = [sys.executable, str(SCRIPT), "--adb", str(fake), "--serial", "SYNTHETIC_SERIAL_PRIVATE", "--release-apk", str(apk), "--phase", phase, "--output", str(output)]
            if include_ui:
                command.append("--include-ui")
            env = dict(os.environ, SYNTHETIC_ADB_FIXTURE=str(mapping_path))
            run = subprocess.run(command, env=env, capture_output=True, text=True)
            report = json.loads(output.read_text())
            before = output.read_bytes()
            repeat = subprocess.run(command, env=env, capture_output=True, text=True)
            self.assertNotEqual(repeat.returncode, 0)
            self.assertEqual(before, output.read_bytes())
            self.assertNotIn(SECRET, output.read_text())
            self.assertNotIn("SYNTHETIC_SERIAL_PRIVATE", output.read_text())
            self.assertEqual(sorted(p.name for p in root.iterdir()), ["fake-adb", "fixture.json", "result.json", "synthetic.apk"])
            return run, report

    def fixture(self):
        return {
            "get-state": "device",
            "shell getprop sys.boot_completed": "1",
            "shell getprop ro.build.version.sdk": "34",
            "shell getprop ro.kernel.qemu": "1",
            "shell pm list features": "feature:android.hardware.type.automotive",
            "shell dumpsys package studio.ax.arrival": "versionCode=12 minSdk=28\npleos.car.permission.NAVI_ROUTE: granted=true\n" + SECRET,
            "shell pm path studio.ax.arrival": "package:/data/app/test/base.apk",
            "shell sha256sum /data/app/test/base.apk": "EXPECTED_DIGEST  /data/app/test/base.apk",
            "shell dumpsys car_service --services CarUxRestrictionsManagerService": "*CarUxRestrictionsManagerService*\nPort: 0x00 UXR: DO: false UxR: 0 time: 777\n" + SECRET,
            "shell -tt uiautomator dump /dev/tty": f'<hierarchy><node package="{collector.PACKAGE}" class="android.widget.TextView" text="{SECRET}"/></hierarchy>',
        }

    def test_complete_read_only_capture_never_declares_live_integration(self):
        run, report = self.run_fake_adb(self.fixture(), phase="nav-match")
        self.assertEqual(run.returncode, 0, run.stderr)
        self.assertEqual(report["collection_status"], "snapshot_collected")
        self.assertTrue(report["app"]["release_hash_matches"])
        self.assertFalse(report["integration_verified"])
        self.assertFalse(report["device"]["pleos_identity_verified"])
        self.assertEqual(report["ui"]["navigation_labels_seen"], [])

    def test_nonautomotive_and_hash_mismatch_are_not_upgraded(self):
        fixture = self.fixture()
        fixture["shell pm list features"] = "feature:android.hardware.camera"
        fixture["shell sha256sum /data/app/test/base.apk"] = "0" * 64 + "  /data/app/test/base.apk"
        fixture["shell dumpsys car_service --services CarUxRestrictionsManagerService"] = "Can't find service: car_service"
        _, report = self.run_fake_adb(fixture)
        self.assertFalse(report["device"]["automotive_feature"])
        self.assertFalse(report["app"]["release_hash_matches"])
        self.assertEqual(report["ux_restrictions"]["format"], "unknown")
        self.assertFalse(report["integration_verified"])

    def test_unavailable_device_stops_without_other_reads(self):
        run, report = self.run_fake_adb({"get-state": "offline"})
        self.assertEqual(run.returncode, 1)
        self.assertEqual(report["collection_status"], "device_unavailable")
        self.assertEqual(len(report["commands"]), 1)

    def test_failed_service_read_remains_partial_and_unknown(self):
        fixture = self.fixture()
        del fixture["shell dumpsys car_service --services CarUxRestrictionsManagerService"]
        run, report = self.run_fake_adb(fixture)
        self.assertEqual(run.returncode, 1)
        self.assertEqual(report["collection_status"], "partial_snapshot")
        self.assertEqual(report["ux_restrictions"]["format"], "unknown")
        self.assertFalse(report["integration_verified"])

    def test_untrusted_apk_path_never_becomes_a_shell_command(self):
        fixture = self.fixture()
        fixture["shell pm path studio.ax.arrival"] = "package:/data/app/test/base.apk; echo " + SECRET
        run, report = self.run_fake_adb(fixture, include_ui=False)
        self.assertEqual(run.returncode, 0)
        self.assertIsNone(report["app"]["installed_sha256"])
        self.assertNotIn("installed_apk_sha256", [entry["label"] for entry in report["commands"]])
        self.assertEqual(report["ui"]["status"], "not_requested")


if __name__ == "__main__":
    unittest.main()
