from faster_whisper import WhisperModel

from app.config.settings import settings


class SpeechService:

    def __init__(self):
        self.model = WhisperModel(
            settings.WHISPER_MODEL,
            device=settings.WHISPER_DEVICE,
            compute_type=settings.WHISPER_COMPUTE_TYPE
        )

    def transcribe(self, audio_path: str) -> tuple[str, str]:

        segments, info = self.model.transcribe(
            audio_path,
            beam_size=5
        )

        text = " ".join(
            segment.text.strip()
            for segment in segments
        ).strip()

        return text, info.language


speech_service = SpeechService()