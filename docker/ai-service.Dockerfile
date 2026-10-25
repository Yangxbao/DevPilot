FROM python:3.11-slim
WORKDIR /app
COPY ai-service/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY ai-service/app ./app
RUN mkdir -p /app/data/index-store
ENV AI_DATA_DIR=/app/data/index-store \
    PYTHONUNBUFFERED=1
EXPOSE 8200
HEALTHCHECK --interval=15s --timeout=5s --start-period=15s --retries=5 \
  CMD python -c "import urllib.request;urllib.request.urlopen('http://127.0.0.1:8200/health')" || exit 1
CMD ["python", "-m", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8200"]
