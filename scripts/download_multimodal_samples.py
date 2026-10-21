
from pathlib import Path
import json
import re
import traceback

import numpy as np
import soundfile as sf
from PIL import Image, ImageDraw
from datasets import load_dataset, Audio

# --------------------------------------------------
# Configuration
# --------------------------------------------------
LIMIT = 50
ROOT = Path(__file__).resolve().parent.parent / "datasets"

AUDIO_DIR = ROOT / "audio"
TEXT_DIR = ROOT / "documents"
IMAGE_DIR = ROOT / "images"
DOC_IMAGE_DIR = ROOT / "document-images"
MANIFEST_DIR = ROOT / "manifests"

for folder in [
    AUDIO_DIR, TEXT_DIR, IMAGE_DIR, DOC_IMAGE_DIR, MANIFEST_DIR
]:
    folder.mkdir(parents=True, exist_ok=True)

manifest = []
errors = []


def safe_name(value, fallback):
    value = re.sub(r"[^a-zA-Z0-9_-]+", "_", str(value))
    return value.strip("_")[:70] or fallback


def record(category, path, metadata=None):
    manifest.append({
        "category": category,
        "path": str(path),
        "metadata": metadata or {}
    })


def report_error(category, exc):
    message = f"{category}: {type(exc).__name__}: {exc}"
    print(f"[ERROR] {message}")
    errors.append(message)


# --------------------------------------------------
# 1. AUDIO: LibriSpeech (up to 50 audio samples)
# --------------------------------------------------

def download_audio():
    import shutil

    print("\n[1/4] Downloading audio samples without decoding...")

    ds = load_dataset(
        "openslr/librispeech_asr",
        "clean",
        split="train.100",
        streaming=True,
    )

    # Disable automatic TorchCodec decoding.
    ds = ds.cast_column("audio", Audio(decode=False))

    count = 0

    for i, item in enumerate(ds.take(LIMIT), start=1):
        audio = item.get("audio") or {}
        audio_bytes = audio.get("bytes")
        audio_path = audio.get("path")

        if audio_bytes:
            output_path = AUDIO_DIR / f"librispeech_{i:03d}.flac"
            output_path.write_bytes(audio_bytes)

        elif audio_path and Path(audio_path).is_file():
            source = Path(audio_path)
            suffix = source.suffix or ".flac"
            output_path = AUDIO_DIR / f"librispeech_{i:03d}{suffix}"
            shutil.copy2(source, output_path)

        else:
            print(f"Skipping sample {i}: audio data unavailable")
            continue

        record("audio", output_path, {
            "dataset": "openslr/librispeech_asr",
            "transcription": item.get("text", ""),
        })

        count += 1
        print(f"Saved audio {count}/{LIMIT}")

    print(f"Audio samples saved: {count}")

    if count == 0:
        raise RuntimeError("No audio files were downloaded.")



# # --------------------------------------------------
# # 2. TEXT: AG News (up to 50 article texts)
# # --------------------------------------------------
# def download_text():
#     print("\n[2/4] Downloading text samples...")

#     ds = load_dataset(
#         "fancyzhx/ag_news",
#         split="train",
#         streaming=True,
#     )

#     count = 0

#     for i, item in enumerate(ds.take(LIMIT), start=1):
#         text = str(item.get("text", "")).strip()
#         if not text:
#             continue

#         path = TEXT_DIR / f"article_{i:03d}.txt"
#         path.write_text(text + "\n", encoding="utf-8")

#         record("text", path, {
#             "dataset": "fancyzhx/ag_news",
#             "label": item.get("label")
#         })

#         count += 1
#         print(f"  Saved text {count}/{LIMIT}")

#     print(f"Text documents saved: {count}")


# # --------------------------------------------------
# # 3. IMAGES: Beans image dataset (up to 50 images)
# # --------------------------------------------------
# def download_images():
#     print("\n[3/4] Downloading image samples...")

#     ds = load_dataset(
#         "AI-Lab-Makerere/beans",
#         split="train",
#         streaming=True,
#     )

#     count = 0

#     for i, item in enumerate(ds.take(LIMIT), start=1):
#         image = item.get("image")
#         if image is None:
#             continue

#         path = IMAGE_DIR / f"image_{i:03d}.jpg"
#         image.convert("RGB").save(path, "JPEG", quality=90)

#         record("image", path, {
#             "dataset": "AI-Lab-Makerere/beans",
#             "label": item.get("labels", item.get("label"))
#         })

#         count += 1
#         print(f"  Saved image {count}/{LIMIT}")

#     print(f"Regular images saved: {count}")


# # --------------------------------------------------
# # 4. DOCUMENT IMAGES: Generate OCR test pages
# #    from downloaded text documents.
# #    These are generated locally, not downloaded.
# # --------------------------------------------------
# def create_document_images():
#     print("\n[4/4] Creating document-image OCR samples...")

#     font = None
#     width, height = 1000, 1400
#     count = 0

#     text_files = sorted(TEXT_DIR.glob("*.txt"))[:LIMIT]

#     for i, text_path in enumerate(text_files, start=1):
#         text = text_path.read_text(encoding="utf-8").strip()
#         image = Image.new("RGB", (width, height), "white")
#         draw = ImageDraw.Draw(image)

#         draw.text((60, 50), f"Sample Document {i}", fill="black", font=font)
#         draw.line((60, 100, 940, 100), fill="black", width=2)

#         # Wrap text to fit the page.
#         words = text.split()
#         lines = []
#         current = ""

#         for word in words:
#             candidate = f"{current} {word}".strip()
#             if len(candidate) > 75:
#                 lines.append(current)
#                 current = word
#             else:
#                 current = candidate

#         if current:
#             lines.append(current)

#         y = 140
#         for line in lines:
#             if y > height - 80:
#                 break
#             draw.text((60, y), line, fill="black", font=font)
#             y += 32

#         path = DOC_IMAGE_DIR / f"document_{i:03d}.png"
#         image.save(path)

#         record("document_image", path, {
#             "generated_from": str(text_path),
#             "purpose": "OCR testing"
#         })

#         count += 1

#     print(f"Document images generated: {count}")


# --------------------------------------------------
# Run all categories independently
# --------------------------------------------------
tasks = [
    ("audio", download_audio)
    # ("text", download_text),
    # ("images", download_images),
    # ("document_images", create_document_images),
]

for name, task in tasks:
    try:
        task()
    except Exception as exc:
        report_error(name, exc)
        traceback.print_exc()

manifest_path = MANIFEST_DIR / "manifest.json"
manifest_path.write_text(
    json.dumps(manifest, indent=2, ensure_ascii=False),
    encoding="utf-8",
)

error_path = MANIFEST_DIR / "download_errors.txt"
error_path.write_text("\n".join(errors), encoding="utf-8")

print("\n======================================")
print("Multimodal sample preparation complete")
print(f"Total files recorded: {len(manifest)}")
print(f"Manifest: {manifest_path}")
print(f"Errors: {error_path}")
print("======================================")
