import pytesseract

from PIL import Image


class OCRService:

    @staticmethod
    def extract_text(image: Image.Image) -> str:
        text = pytesseract.image_to_string(image)

        return text.strip()