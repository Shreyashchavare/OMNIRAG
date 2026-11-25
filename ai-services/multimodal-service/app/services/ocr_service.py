try:
    import pytesseract
except ImportError:
    pytesseract = None

from PIL import Image


class OCRService:

    @staticmethod
    def extract_text(image: Image.Image) -> str:
        if pytesseract is None:
            raise RuntimeError("pytesseract is not installed")

        converted = None
        try:
            target_image = image
            if hasattr(image, "mode") and image.mode in ("RGBA", "P"):
                converted = image.convert("RGB")
                target_image = converted

            text = pytesseract.image_to_string(target_image)
            return text.strip()
        finally:
            if converted is not None:
                try:
                    converted.close()
                except Exception:
                    pass
