try:
    from faster_whisper import WhisperModel
except ImportError:
    WhisperModel = None

from app.config.settings import settings


class SpeechService:

    def __init__(self):
        if WhisperModel is not None:
            self.model = WhisperModel(
                settings.WHISPER_MODEL,
                device=settings.WHISPER_DEVICE,
                compute_type=settings.WHISPER_COMPUTE_TYPE
            )
        else:
            self.model = None

    def transcribe(self, audio_path: str) -> tuple[str, str]:
        if self.model is None:
            raise RuntimeError("faster_whisper is not installed")

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