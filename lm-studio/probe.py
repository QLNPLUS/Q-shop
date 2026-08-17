#!/usr/bin/env python3
"""Auto-detect the reachable LM Studio endpoint and call qwen VL to describe an image.

Usage:
    python probe.py                     # probe endpoints only, print the fastest reachable one
    python probe.py IMAGE_PATH          # probe then ask qwen VL to describe the image
    python probe.py IMAGE_PATH --json   # probe then return a structured Chinese description (JSON)

Config is read from endpoints.json next to this file.
Nothing here depends on third-party packages (stdlib urllib only).
"""
from __future__ import annotations

import base64
import concurrent.futures as cf
import json
import os
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

# Force UTF-8 text encoding for stdout/stderr regardless of the Windows code page,
# so the model's Chinese replies are not garbled by a legacy console encoding.
for _stream in (sys.stdout, sys.stderr):
    _stream.reconfigure(encoding="utf-8", errors="replace")  # type: ignore[attr-defined]

HERE = Path(__file__).resolve().parent
CONFIG_PATH = HERE / "endpoints.json"

DEFAULT_CONFIG: dict = {
    "endpoints": ["http://192.168.1.3:1234"],
    "apiKey": "",
    "visionModel": "qwen/qwen3.5-9b",
    "probeTimeoutSec": 5,
    "requestTimeoutSec": 60,
}


def load_config() -> dict:
    if CONFIG_PATH.exists():
        try:
            merged = dict(DEFAULT_CONFIG)
            merged.update(json.loads(CONFIG_PATH.read_text(encoding="utf-8")))
            return merged
        except Exception as exc:  # noqa: BLE001 - config corruption should not crash silently
            print(f"[warn] failed to read {CONFIG_PATH}: {exc}; using defaults", file=sys.stderr)
    return dict(DEFAULT_CONFIG)


def probe(ep: str, api_key: str, timeout_sec: float) -> dict:
    """Return {'ep','ok','ms','status','models'} for one endpoint."""
    url = f"{ep}/v1/models"
    req = urllib.request.Request(url)
    if api_key:
        req.add_header("Authorization", f"Bearer {api_key}")
    t0 = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=timeout_sec) as resp:
            ms = int((time.perf_counter() - t0) * 1000)
            body = json.loads(resp.read().decode("utf-8", "replace"))
            ids = [m.get("id") for m in body.get("data", []) if isinstance(m, dict)]
            return {"ep": ep, "ok": True, "ms": ms, "status": resp.status, "models": ids}
    except urllib.error.HTTPError as exc:
        # 401 still means the service is up (auth required)
        ms = int((time.perf_counter() - t0) * 1000)
        ok = exc.code in (200, 401, 403)
        return {"ep": ep, "ok": ok, "ms": ms, "status": exc.code, "models": []}
    except Exception as exc:  # noqa: BLE001 - network/timeout; report as unreachable
        ms = int((time.perf_counter() - t0) * 1000)
        return {"ep": ep, "ok": False, "ms": ms, "status": None, "models": [], "error": str(exc)}


def pick_endpoint(cfg: dict) -> dict:
    """Probe all endpoints concurrently; return the fastest reachable one ({} if none)."""
    probes = []
    with cf.ThreadPoolExecutor(max_workers=len(cfg["endpoints"])) as ex:
        futures = [ex.submit(probe, ep, cfg["apiKey"], cfg["probeTimeoutSec"]) for ep in cfg["endpoints"]]
        for fut in futures:
            try:
                probes.append(fut.result())
            except Exception as exc:  # noqa: BLE001
                probes.append({"ep": "?", "ok": False, "ms": 0, "status": None, "models": [], "error": str(exc)})
    ok = [p for p in probes if p["ok"]]
    ok.sort(key=lambda p: p["ms"])
    return ok[0] if ok else {}


def image_to_data_uri(path: str) -> str:
    mime = "image/png"
    ext = Path(path).suffix.lower()
    mime_map = {".jpg": "image/jpeg", ".jpeg": "image/jpeg", ".png": "image/png", ".webp": "image/webp", ".gif": "image/gif"}
    mime = mime_map.get(ext, mime)
    b64 = base64.b64encode(Path(path).read_bytes()).decode("ascii")
    return f"data:{mime};base64,{b64}"


def describe_image(base_url: str, cfg: dict, image_path: str, structured: bool = False) -> str:
    prompt_text = (
        "请用中文描述这张图片的内容。尽量详细：画面主体、文字内容、布局、颜色、图标等。"
        if structured
        else "用中文描述这张图片的内容。可读到的文字请逐行列出。"
    )
    content = [
        {"type": "text", "text": prompt_text},
        {"type": "image_url", "image_url": {"url": image_to_data_uri(image_path)}},
    ]
    if structured:
        content.append(
            {
                "type": "text",
                "text": "\n请把描述整理成如下JSON输出，不要加额外文字：{\"主体\":\"...\",\"文字\":[\"...\"],\"布局\":\"...\",\"颜色\":\"...\",\"其他\":\"...\"}",
            }
        )
    payload = {
        "model": cfg["visionModel"],
        "messages": [{"role": "user", "content": content}],
        "max_tokens": 512,
        "temperature": 0.3,
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


def main() -> int:
    cfg = load_config()
    ep = pick_endpoint(cfg)
    if not ep:
        print("NO_REACHABLE_ENDPOINT", file=sys.stderr)
        return 1

    print(f"USING {ep['ep']}  (ok={ep['status']}, {ep['ms']}ms)")
    if ep.get("models"):
        print(f"  models: {', '.join(ep['models'])}")

    image_arg = sys.argv[1] if len(sys.argv) > 1 else None
    structured = "--json" in sys.argv[1:]
    if not image_arg:
        return 0

    if not Path(image_arg).exists():
        print(f"IMAGE_NOT_FOUND: {image_arg}", file=sys.stderr)
        return 2

    print(f"DESCRIBING {image_arg} via {cfg['visionModel']} ...", file=sys.stderr)
    text = describe_image(ep["ep"], cfg, image_arg, structured=structured)
    print("=== MODEL REPLY ===")
    print(text)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
