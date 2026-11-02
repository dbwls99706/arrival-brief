#!/usr/bin/env python3
"""Cross-decode the production Kotlin QR writer and recorded Android screenshots.

Requires Python Pillow, NumPy, OpenCV, Node, a JDK, cached Kotlin compiler jars,
the same ZXing core jar used by Android, and a separately downloaded jsQR bundle.
No dependencies or network resources are installed/fetched by this script.
Synthetic image transforms are decoder stress tests, not physical camera tests.
"""
from __future__ import annotations

import argparse
import base64
import hashlib
import io
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile

import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageFilter
from PIL import __version__ as pillow_version

ROOT = Path(__file__).resolve().parents[1]
SOURCE_DIR = ROOT / "android/app/src/main/java/studio/ax/arrival"
SOURCE_NAMES = ["ParkingMemoQr.kt", "VisitCompanion.kt", "VehicleGateway.kt"]
NOW = 1_800_000_000_000
JSQR_SHA256 = "3325b0888fa4745c4e6940897d8c4f426fbaae76901fcbfe1871a04e90a51655"
CASES = {
    "typical_ko": "B3 블루존 E12",
    "korean_64": "한" * 64,
    "ascii_64": "A" * 64,
    "emoji_32_utf16_64": "🚗" * 32,
    "mixed_symbols": "B2 🅿️ · P7 / 출구 2번 ↗️",
    "combining_marks": "Cafe\u0301 · A12 · 🅿️",
    "varied_korean_64": ("가나다라마바사아자차카타파하거너더러머버서어저처커터퍼허" * 3)[:64],
    "long_location_64": ("지하삼층블루존동쪽출입구기둥옆엘리베이터에서오른쪽통로끝" * 3)[:64],
    "varied_emoji_32_utf16_64": "🚗🚙🚕🚘" * 8,
    "combining_64_utf16": "e\u0301" * 32,
}

KOTLIN_RUNNER = r'''
import studio.ax.arrival.*
import java.io.File
import java.util.Base64
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
fun main(args: Array<String>) {
    val out = File(args[0])
    val rows = File(args[1]).readLines()
    val now = args[2].toLong()
    val sizes = args[3].split(",").map { it.toInt() }
    for (row in rows) {
        val fields = row.split("\t")
        val text = String(Base64.getDecoder().decode(fields[1]), Charsets.UTF_8)
        val record = ParkingMemo(text, now)
        val content = ParkingMemoQr.payload(record, now)
        check(content != null) { "Fixture rejected: ${fields[0]}" }
        File(out, fields[0] + ".txt").writeText(content)
        for (size in sizes) {
            val matrix = ParkingMemoQr.encode(record, now, size)!!
            val image = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
            for (y in 0 until size) for (x in 0 until size)
                image.setRGB(x, y, if (matrix[x, y]) 0 else 0xFFFFFF)
            ImageIO.write(image, "png", File(out, fields[0] + "-" + size + ".png"))
        }
    }
    val tooLong = ParkingMemo("🚗".repeat(33), now)
    check(ParkingMemoQr.payload(tooLong, now) == null)
    check(ParkingMemoQr.payload(ParkingMemo("A".repeat(65), now), now) == null)
    check(ParkingMemoQr.payload(ParkingMemo("한".repeat(65), now), now) == null)
}
'''

NODE_RUNNER = r'''
const read = require("node:readline").createInterface({input: process.stdin});
const qr = require(process.argv[1]);
read.on("line", line => {
  try {
    const x = JSON.parse(line), pixels = Buffer.from(x.pixels, "base64");
    const found = qr(new Uint8ClampedArray(pixels), x.width, x.height, {inversionAttempts:"attemptBoth"});
    process.stdout.write(JSON.stringify(found ? {text:found.data, version:found.version} : {text:null})+"\n");
  } catch (e) { process.stdout.write(JSON.stringify({error:String(e)})+"\n"); }
});
'''


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def run(argv: list[str], **kwargs):
    return subprocess.run(argv, check=True, capture_output=True, text=True, timeout=120, **kwargs)


def produce_fixtures(args, target):
    jars = sorted(Path(args.kotlin_lib).glob("*.jar"))
    stdlib = next(x for x in jars if x.name.startswith("kotlin-stdlib-") and "extensions" not in x.name)
    annotation = next(x for x in jars if x.name.startswith("annotations-"))
    source = target / "QrFixtures.kt"
    source.write_text(KOTLIN_RUNNER)
    casefile = target / "fixtures.tsv"
    casefile.write_text("\n".join(k + "\t" + base64.b64encode(v.encode()).decode() for k, v in CASES.items()))
    runtime = os.pathsep.join([str(target / "classes"), str(stdlib), args.zxing_jar, str(annotation)])
    run([args.java, "-Xmx384m", "-cp", os.pathsep.join(map(str, jars)),
         "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-no-stdlib", "-no-reflect", "-jvm-target", "17",
         "-classpath", runtime, "-d", str(target / "classes"),
         *[str(SOURCE_DIR / name) for name in SOURCE_NAMES], str(source)])
    run([args.java, "-Xmx192m", "-Djava.awt.headless=true", "-cp", runtime, "QrFixturesKt", str(target), str(casefile),
         str(args.record_time), ",".join(map(str, args.sizes))])
    return [{"id": f"{name}-{size}", "path": target / f"{name}-{size}.png",
             "expected": (target / f"{name}.txt").read_text(), "generated_size": size,
             "memo_utf16_units": len(text.encode("utf-16-le")) // 2,
             "memo_code_points": len(text)} for name, text in CASES.items() for size in args.sizes]


def transforms(image):
    width, height = image.size
    yield "original", image
    yield "resize_75pct", image.resize((round(width * .75), round(height * .75)), Image.Resampling.LANCZOS)
    yield "rotate_12deg", image.rotate(12, resample=Image.Resampling.BICUBIC, expand=True, fillcolor="white")
    source = np.float32([[0, 0], [width - 1, 0], [width - 1, height - 1], [0, height - 1]])
    target = np.float32([[width * .08, height * .05], [width * .96, 0], [width - 1, height * .96], [0, height - 1]])
    warped = cv2.warpPerspective(np.asarray(image), cv2.getPerspectiveTransform(source, target),
                                 (width, height), flags=cv2.INTER_LINEAR, borderValue=(255, 255, 255))
    yield "perspective_8pct", Image.fromarray(warped)
    yield "gray_45_to_180", image.point(lambda x: round(45 + x * 135 / 255))
    yield "blur_sigma_0_6", image.filter(ImageFilter.GaussianBlur(.6))
    jpeg = io.BytesIO(); image.save(jpeg, format="JPEG", quality=70)
    yield "jpeg_quality_70", Image.open(io.BytesIO(jpeg.getvalue())).convert("RGB")
    smaller = image.resize((round(width * .75), round(height * .75)), Image.Resampling.LANCZOS)
    yield "combined_75pct_rotate8_blur0_45", smaller.rotate(8, resample=Image.Resampling.BICUBIC,
        expand=True, fillcolor="white").filter(ImageFilter.GaussianBlur(.45))


def quiet_zone(image):
    dark = np.asarray(image.convert("L")) < 128
    ys, xs = np.where(dark)
    x0, x1, y0, y1 = int(xs.min()), int(xs.max()), int(ys.min()), int(ys.max())
    run_length = 0
    while x0 + run_length < image.width and dark[y0, x0 + run_length]:
        run_length += 1
    module = run_length / 7
    margins = [x0 / module, y0 / module, (image.width - 1 - x1) / module, (image.height - 1 - y1) / module]
    return {"module_pixels": module, "symbol_modules": (x1 - x0 + 1) / module,
            "quiet_zone_modules_ltrb": margins, "at_least_four_modules": min(margins) >= 4}


class Decoders:
    def __init__(self, args):
        self.cv = cv2.QRCodeDetector()
        self.node = subprocess.Popen([args.node, "-e", NODE_RUNNER, str(Path(args.jsqr).resolve())],
            stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True)

    def decode(self, image):
        rgb = image.convert("RGB")
        opencv, _, _ = self.cv.detectAndDecode(cv2.cvtColor(np.asarray(rgb), cv2.COLOR_RGB2BGR))
        request = {"width": rgb.width, "height": rgb.height,
                   "pixels": base64.b64encode(rgb.convert("RGBA").tobytes()).decode()}
        self.node.stdin.write(json.dumps(request) + "\n"); self.node.stdin.flush()
        line = self.node.stdout.readline()
        if not line:
            raise RuntimeError("jsQR decoder stopped")
        decoded = json.loads(line)
        if "error" in decoded:
            raise RuntimeError(decoded["error"])
        return {"opencv": opencv or None, "jsqr": decoded["text"]}

    def close(self):
        self.node.stdin.close()
        self.node.wait(timeout=10)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--kotlin-lib", required=True, help="Kotlin compiler/stdlib dependency JAR directory; Gradle lib works")
    parser.add_argument("--zxing-jar", required=True)
    parser.add_argument("--jsqr", required=True, help="Unmodified jsQR 1.4.0 dist/jsQR.js")
    parser.add_argument("--java", default="java")
    parser.add_argument("--node", default="node")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--screenshot", type=Path, action="append", help="Actual app QR screenshot; repeat for different font settings")
    parser.add_argument("--require-current-screenshots", action="store_true", help="Fail if captures do not match the current production payload format")
    parser.add_argument("--skip-screenshots", action="store_true", help="Produce source-only evidence while a new Android build is being verified")
    parser.add_argument("--record-time", type=int, default=NOW, help="Deterministic memo save time in Unix milliseconds")
    parser.add_argument("--sizes", type=int, nargs="+", default=[256, 320, 512])
    parser.add_argument("--no-stress", action="store_true", help="Run only undecorated pixels for a targeted save-time/size probe")
    args = parser.parse_args()
    if sha(Path(args.jsqr)) != JSQR_SHA256:
        parser.error("jsQR bytes differ from the pinned 1.4.0 bundle; use the exact upstream file linked in the report")
    if args.record_time <= 0 or any(size < 256 or size > 1024 for size in args.sizes):
        parser.error("record time must be positive and QR sizes must lie in 256..1024")
    if args.skip_screenshots and (args.screenshot or args.require_current_screenshots):
        parser.error("skip-screenshots cannot be combined with screenshot verification options")
    args.output.mkdir(parents=True, exist_ok=True)
    cv2.setNumThreads(1)
    rows, sources, quiet, previews = [], [], [], []
    source_hashes = {name: sha(SOURCE_DIR / name) for name in SOURCE_NAMES}
    decoders = Decoders(args)
    try:
        with tempfile.TemporaryDirectory(prefix="arrival-qr-") as td:
            fixtures = produce_fixtures(args, Path(td))
            for item in fixtures:
                im = Image.open(item["path"]).convert("RGB")
                quiet.append({"id": item["id"], **quiet_zone(im)})
                sources.append({k: v for k, v in item.items() if k not in ("path", "expected")})
                sources[-1].update({"image_sha256": sha(item["path"]),
                    "payload_sha256": hashlib.sha256(item["expected"].encode()).hexdigest(),
                    "payload_utf8_bytes": len(item["expected"].encode())})
                for transform, image in transforms(im):
                    if args.no_stress and transform != "original":
                        continue
                    found = decoders.decode(image)
                    matches = {name: value == item["expected"] for name, value in found.items()}
                    rows.append({"fixture": item["id"], "transform": transform, "pixels": list(image.size),
                        "matches": matches, "incorrect_nonempty": [name for name, value in found.items() if value and value != item["expected"]]})
                    if item["id"] == "korean_64-320" and transform in ("original", "perspective_8pct", "combined_75pct_rotate8_blur0_45"):
                        previews.append((transform, image.copy(), all(matches.values())))
            screenshot_results = []
            screenshots = args.screenshot or [ROOT / "docs/verification" / filename for filename in
                ["android-aosp28-rc1-qr.png", "android-aosp28-rc1-large-qr.png"]]
            if args.skip_screenshots:
                screenshots = []
            current_example = next(x["expected"] for x in fixtures if x["id"].startswith("typical_ko-"))
            stamp_pattern = r"\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2} KST"
            current_stamp = re.search(stamp_pattern, current_example).group()
            for path in screenshots:
                path = path.resolve()
                found = decoders.decode(Image.open(path))
                agrees = bool(found["opencv"]) and len(set(found.values())) == 1
                path_label = str(path.relative_to(ROOT)) if path.is_relative_to(ROOT) else path.name
                screenshot_results.append({"path": path_label, "sha256": sha(path),
                    "both_decoders_agree": agrees,
                    "known_memo_and_copy_notice_match": all(v and "B3 블루존 E12" in v and "휴대폰 복사본" in v and "자동 삭제되지" in v and re.search(stamp_pattern, v) for v in found.values()),
                    "payload_matches_current_production_format": all(v and re.sub(stamp_pattern, current_stamp, v) == current_example for v in found.values()),
                    "decoded_payload": found["opencv"],
                    "timestamp_oracle": "decoded by both engines; not independently recorded by instrumentation"})
            negative = decoders.decode(Image.new("RGB", (320, 320), "white"))
    finally:
        decoders.close()
    if source_hashes != {name: sha(SOURCE_DIR / name) for name in SOURCE_NAMES}:
        raise SystemExit("Production QR sources changed during the run; rerun on a stable source snapshot")
    mandatory = [r for r in rows if r["transform"] == "original"]
    stress = [r for r in rows if r["transform"] != "original"]
    bad_decodes = [r for r in rows if r["incorrect_nonempty"]]
    failures = [r for r in stress if not all(r["matches"].values())]
    report = {
        "schema_version": 1, "scope": "Software cross-decoder and synthetic image-transformation verification; no physical phone or camera test.",
        "fixture_parameters": {"record_saved_at_ms": args.record_time, "sizes": args.sizes, "stress_enabled": not args.no_stress},
        "production_sources": [{"path": str((SOURCE_DIR / n).relative_to(ROOT)), "sha256": source_hashes[n]} for n in SOURCE_NAMES],
        "tool_versions": {"opencv": cv2.__version__, "numpy": np.__version__, "pillow": pillow_version, "jsqr": "1.4.0"},
        "decoder_bundle": {"sha256": sha(Path(args.jsqr)), "source": "https://github.com/cozmo/jsQR/blob/8e6a036beafa7053dd44b1b76ac578d22b1b3311/dist/jsQR.js", "license": "Apache-2.0", "included_in_app": False},
        "encoder_dependency": {"sha256": sha(Path(args.zxing_jar)), "name": "ZXing core 3.5.4"},
        "fixtures": sources, "quiet_zones": quiet, "results": rows,
        "screenshots": screenshot_results, "current_screenshot_format_required": args.require_current_screenshots,
        "screenshots_skipped": args.skip_screenshots,
        "rejected_input_boundaries": ["33 supplementary-plane emoji = 66 UTF-16 units", "65 ASCII units", "65 Korean units"],
        "blank_control": {"no_false_positive": all(v is None for v in negative.values())},
        "summary": {"production_fixtures": len(fixtures), "baseline_comparisons": len(mandatory) * 2,
            "baseline_pass": sum(sum(r["matches"].values()) for r in mandatory),
            "stress_comparisons": len(stress) * 2, "stress_pass": sum(sum(r["matches"].values()) for r in stress),
            "stress_failed_cases": len(failures), "incorrect_nonempty_decodes": len(bad_decodes),
            "screenshot_comparisons": len(screenshot_results) * 2,
            "all_quiet_zones_at_least_four_modules": all(x["at_least_four_modules"] for x in quiet)},
        "stress_failures": failures,
        "limitations": ["The image transforms do not model a particular phone camera, glare, motion, screen brightness or viewing distance.",
            "All distortion failures remain reported; a decoder returning no result is distinct from returning the wrong text.",
            "The Android limit is 64 UTF-16 code units, so 32 supplementary-plane emoji fill that budget.",
            "Web parking notes do not implement QR handoff.",
            "Captured screenshot timestamps are cross-decoder observations, not independent instrumentation time assertions."]}
    (args.output / "completion-qr.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    montage = Image.new("RGB", (900, 340), "#eef3f8")
    draw = ImageDraw.Draw(montage)
    for index, (label, image, passed) in enumerate(previews):
        image.thumbnail((275, 285))
        montage.paste(image, (index * 300 + (300 - image.width) // 2, 38))
        draw.text((index * 300 + 10, 10), label, fill="black")
        draw.text((index * 300 + 10, 322), "both decoders pass" if passed else "see failure report", fill="black")
    # A compact illustrative contact sheet; decoder assertions use the original
    # transformed pixels above, not these scaled and quantized thumbnails.
    montage.quantize(colors=8).save(args.output / "completion-qr-transforms.png", optimize=True)
    print(json.dumps(report["summary"]))
    if any(not all(r["matches"].values()) for r in mandatory) or bad_decodes or not all(
        r["both_decoders_agree"] and r["known_memo_and_copy_notice_match"] for r in screenshot_results
    ) or not report["blank_control"]["no_false_positive"] or not all(x["at_least_four_modules"] for x in quiet) or (
        args.require_current_screenshots and not all(x["payload_matches_current_production_format"] for x in screenshot_results)
    ):
        raise SystemExit("Mandatory QR verification failed; see completion-qr.json")


if __name__ == "__main__":
    main()
