FROM python:3.10-slim

WORKDIR /app

RUN pip install --no-cache-dir fastapi==0.110.0 uvicorn==0.28.0 python-multipart==0.0.9 pydantic==2.6.4

COPY main.py .

EXPOSE 8000

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
