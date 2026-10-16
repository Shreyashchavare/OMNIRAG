import pymupdf

class PDFService:

    @staticmethod
    def extract_text(file_bytes: bytes) -> tuple[str, int]:

        with pymupdf.open(
            stream=file_bytes,
            filetype="pdf"
        ) as document:

            pages = []

            for page in document:
                text = page.get_text()

                if text:
                    pages.append(text)

            page_count = document.page_count

        return "\n\n".join(pages).strip(), page_count