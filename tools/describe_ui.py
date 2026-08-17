#!/usr/bin/env python3
"""Describe a UI screenshot with a targeted question via LM Studio qwen VL."""
import base64
import json
import sys
import urllib.request
from pathlib import Path

sys.path.insert(0, r"C:\Users\QLN\deepseek-harness\workspace\lm-studio")
from probe import load_config, pick_endpoint  # noqa: E402

img = sys.argv[1]
question = sys.argv[2] if len(sys.argv) > 2 else "请用中文详细描述这张图片的内容和布局。"


def describe(base_url, cfg, image_path, question_text):
    ext = Path(image_path).suffix.lower()
    mime = {".jpg": "image/jpeg", ".jpeg": "image/jpeg", ".png": "image/png", ".webp": "image/webp"}.get(ext, "image/png")
    b64 = base64.b64encode(Path(image_path).read_bytes()).decode("ascii")
    content = [
        {"type": "text", "text": question_text},
        {"type": "image_url", "image_url": {"url": f"data:{mime};base64,{b64}"}},
    ]
    payload = {
        "model": cfg["visionModel"],
        "messages": [{"role": "user", "content": content}],
        "max_tokens": 700,
        "temperature": 0.2,
    }
    req = urllib.request.Request(
        f"{base_url}/v1/chat/completions",
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json", "Authorization": f"Bearer {cfg['apiKey']}"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=cfg["requestTimeoutSec"]) as resp:
        body = json.loads(resp.read().decode("utf-8", "replace"))
    return body["choices"][0]["message"]["content"]


cfg = load_config()
ep = pick_endpoint(cfg)
if not ep:
    print("NO_REACHABLE_ENDPOINT", file=sys.stderr)
    sys.exit(1)
print(f"USING {ep['ep']}", file=sys.stderr)
print(describe(ep["ep"], cfg, img, question))
