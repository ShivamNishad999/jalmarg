import os
import sys
import json
import logging
from http.server import HTTPServer, BaseHTTPRequestHandler
from pathlib import Path

# Add ml/src to sys.path
src_dir = Path(__file__).resolve().parent.parent.parent
sys.path.insert(0, str(src_dir))
root_dir = src_dir.parent.parent

from jalmarg_ml.vision.inference_service import VisionInferenceService

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("VisionServer")

PORT = int(os.environ.get("JALMARG_VISION_PORT", "8092"))
HOST = "127.0.0.1"

artifacts_path = root_dir / "ml" / "artifacts" / "vision" / "best_checkpoint.pt"
inference_service = VisionInferenceService(str(artifacts_path) if artifacts_path.exists() else None)

class VisionRequestHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/health" or self.path == "/readiness":
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            status = {
                "status": "UP",
                "modelAvailable": inference_service.is_model_available(),
                "modelVersion": VisionInferenceService.MODEL_VERSION,
                "checkpointExists": artifacts_path.exists()
            }
            self.wfile.write(json.dumps(status).encode("utf-8"))
        else:
            self.send_response(404)
            self.end_headers()

    def do_POST(self):
        if self.path == "/predict":
            content_length = int(self.headers.get("Content-Length", 0))
            if content_length <= 0:
                self._send_json(400, {
                    "modelVersion": VisionInferenceService.MODEL_VERSION,
                    "status": "FAILED",
                    "prediction": "INCONCLUSIVE",
                    "failureReason": "EMPTY_REQUEST_BODY",
                    "reviewRequired": True
                })
                return

            if content_length > VisionInferenceService.MAX_IMAGE_SIZE_BYTES:
                self._send_json(413, {
                    "modelVersion": VisionInferenceService.MODEL_VERSION,
                    "status": "FAILED",
                    "prediction": "INCONCLUSIVE",
                    "failureReason": "PAYLOAD_TOO_LARGE",
                    "reviewRequired": True
                })
                return

            image_bytes = self.rfile.read(content_length)
            result = inference_service.predict(image_bytes)
            self._send_json(200, result)
        else:
            self.send_response(404)
            self.end_headers()

    def _send_json(self, code: int, data: dict):
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(json.dumps(data).encode("utf-8"))

    def log_message(self, format, *args):
        # Suppress noisy raw request logging to avoid leaking paths
        logger.debug("%s - %s", self.address_string(), format % args)

def run():
    server = HTTPServer((HOST, PORT), VisionRequestHandler)
    logger.info(f"JalMarg Computer Vision Inference Server running on http://{HOST}:{PORT}")
    logger.info(f"Model available: {inference_service.is_model_available()}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        logger.info("Stopping Vision Server...")
        server.server_close()

if __name__ == '__main__':
    run()
