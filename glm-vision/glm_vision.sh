#!/usr/bin/env bash
set -euo pipefail

# ============================================================
# glm_vision.sh — Call GLM-4V-FLASH for image understanding
# Requires: curl, python3, base64, file
# ============================================================

API_ENDPOINT="https://open.bigmodel.cn/api/paas/v4/chat/completions"
MODEL="${GLM_VISION_MODEL:-glm-4v-flash}"
MAX_IMAGE_SIZE="${GLM_VISION_MAX_SIZE:-10485760}"  # 10 MB

usage() {
  cat <<EOF
Usage: glm_vision.sh <image_path> [prompt]

Arguments:
  image_path    Path to the image file (jpg, png, gif, webp, bmp)
  prompt        Question or instruction for the vision model
                (default: 请详细描述这张图片的内容。)

Environment:
  GLM_API_KEY           Required. Your Zhipu AI (GLM) API key.
  GLM_VISION_MODEL      Optional. Model name (default: glm-4v-flash).
  GLM_VISION_MAX_SIZE   Optional. Max image size in bytes (default: 10485760).
  GLM_VISION_TIMEOUT    Optional. Request timeout in seconds (default: 60).

Config file:
  If GLM_API_KEY is not set, the script reads from:
    ~/.linecode/.glm_api_key

Examples:
  glm_vision.sh photo.jpg
  glm_vision.sh screenshot.png "Extract all text from this image"
  GLM_API_KEY=xxx glm_vision.sh diagram.png "Explain this architecture"
  echo "What is shown?" | glm_vision.sh img.jpg -    # read prompt from stdin
EOF
  exit 1
}

# ---- Parse args ----
[[ $# -lt 1 ]] && usage

IMAGE_PATH="$1"
PROMPT_ARG="${2:-}"

# Read prompt from stdin if "-" is given
if [[ "$PROMPT_ARG" == "-" ]]; then
  PROMPT=$(cat)
elif [[ -n "$PROMPT_ARG" ]]; then
  PROMPT="$PROMPT_ARG"
else
  PROMPT="请详细描述这张图片的内容。"
fi

# ---- API Key ----
API_KEY="${GLM_API_KEY:-}"
if [[ -z "$API_KEY" ]]; then
  CONFIG_FILE="${HOME}/.linecode/.glm_api_key"
  if [[ -f "$CONFIG_FILE" ]]; then
    API_KEY=$(tr -d '[:space:]' < "$CONFIG_FILE")
  fi
fi
if [[ -z "$API_KEY" ]]; then
  echo "ERROR: GLM_API_KEY not set." >&2
  echo "  Export it:  export GLM_API_KEY=your_key" >&2
  echo "  Or save to: ~/.linecode/.glm_api_key" >&2
  exit 1
fi

# ---- File checks ----
if [[ ! -f "$IMAGE_PATH" ]]; then
  echo "ERROR: File not found: $IMAGE_PATH" >&2
  exit 1
fi

FILE_SIZE=$(stat -c%s "$IMAGE_PATH" 2>/dev/null || stat -f%z "$IMAGE_PATH" 2>/dev/null || echo 0)
if [[ "$FILE_SIZE" -eq 0 ]]; then
  echo "ERROR: File is empty or unreadable: $IMAGE_PATH" >&2
  exit 1
fi
if [[ "$FILE_SIZE" -gt "$MAX_IMAGE_SIZE" ]]; then
  echo "ERROR: Image too large ($FILE_SIZE bytes > $MAX_IMAGE_SIZE)." >&2
  echo "  Increase limit: export GLM_VISION_MAX_SIZE=20971520" >&2
  exit 1
fi

# ---- Build request JSON via python3 ----
TMPFILE=$(mktemp)
trap 'rm -f "$TMPFILE"' EXIT

python3 - "$IMAGE_PATH" "$PROMPT" "$MODEL" "$TMPFILE" <<'PYEOF'
import base64, json, mimetypes, os, subprocess, sys

image_path, prompt, model, outfile = sys.argv[1:5]

# Detect MIME type
mime_type, _ = mimetypes.guess_type(image_path)
if mime_type is None or not mime_type.startswith("image/"):
    try:
        result = subprocess.run(
            ["file", "--mime-type", "-b", image_path],
            capture_output=True, text=True
        )
        mime_type = result.stdout.strip()
    except Exception:
        mime_type = "image/jpeg"
if not mime_type.startswith("image/"):
    mime_type = "image/jpeg"

# Read and base64-encode image
with open(image_path, "rb") as f:
    img_b64 = base64.b64encode(f.read()).decode("utf-8")

data_url = f"data:{mime_type};base64,{img_b64}"

payload = {
    "model": model,
    "messages": [
        {
            "role": "user",
            "content": [
                {"type": "text", "text": prompt},
                {"type": "image_url", "image_url": {"url": data_url}},
            ],
        }
    ],
    "temperature": 0.3,
}

with open(outfile, "w") as f:
    json.dump(payload, f, ensure_ascii=False)
PYEOF

# ---- Call API ----
TIMEOUT="${GLM_VISION_TIMEOUT:-60}"

HTTP_RESPONSE=$(curl -s -w '\n{"_http_code":"%{http_code}"}' \
  -X POST "$API_ENDPOINT" \
  -H "Authorization: Bearer $API_KEY" \
  -H "Content-Type: application/json" \
  -d @"$TMPFILE" \
  --max-time "$TIMEOUT" 2>&1)

# ---- Parse response ----
HTTP_CODE=$(printf '%s' "$HTTP_RESPONSE" | tail -1 | python3 -c "
import json, sys
try:
    print(json.load(sys.stdin).get('_http_code', '000'))
except Exception:
    print('000')
" 2>/dev/null || echo "000")

RESPONSE_BODY=$(printf '%s' "$HTTP_RESPONSE" | sed '$d')

if [[ "$HTTP_CODE" != "200" ]]; then
  echo "ERROR: API request failed (HTTP $HTTP_CODE)" >&2
  printf '%s' "$RESPONSE_BODY" | python3 -m json.tool 2>/dev/null >&2 || printf '%s\n' "$RESPONSE_BODY" >&2
  exit 1
fi

# Extract and print content
printf '%s' "$RESPONSE_BODY" | python3 -c "
import json, sys
try:
    data = json.load(sys.stdin)
    # Check for API-level error
    if 'error' in data:
        err = data['error']
        msg = err.get('message', str(err)) if isinstance(err, dict) else str(err)
        print(f'API ERROR: {msg}', file=sys.stderr)
        sys.exit(1)
    content = data['choices'][0]['message']['content']
    print(content)
except (KeyError, IndexError) as e:
    print(f'ERROR: Unexpected response format: {e}', file=sys.stderr)
    print(json.dumps(data, indent=2, ensure_ascii=False), file=sys.stderr)
    sys.exit(1)
except json.JSONDecodeError as e:
    print(f'ERROR: Invalid JSON response: {e}', file=sys.stderr)
    sys.exit(1)
"
