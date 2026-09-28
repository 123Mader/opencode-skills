#!/data/data/com.termux/files/usr/bin/bash
# docfmt-review: 文档排版审查（中文标点/空格/全半角 + Markdown 结构 + 公文格式）
set -euo pipefail
export PYTHONIOENCODING=utf-8

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FILES=()
FIX=0
OFFICIAL=0

usage() {
    cat <<'EOF'
用法: docfmt.sh <文件...> [选项]

  选项:
    --official   追加党政机关公文格式 (GB/T 9704-2012) 专项检查
    --fix        自动修复安全项（原文件备份 .bak）
    -h, --help   本帮助

示例:
  docfmt.sh 报告.md
  docfmt.sh 报告.md --official
  docfmt.sh a.md b.txt --fix
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --fix) FIX=1 ;;
        --official) OFFICIAL=1 ;;
        -h|--help) usage; exit 0 ;;
        -*) echo "未知选项: $1" >&2; usage; exit 1 ;;
        *) FILES+=("$1") ;;
    esac
    shift
done
[ ${#FILES[@]} -eq 0 ] && { echo "错误: 缺少文件参数" >&2; usage; exit 1; }
for f in "${FILES[@]}"; do
    [ -f "$f" ] || { echo "错误: 文件不存在: $f" >&2; exit 1; }
done

python3 - "$FIX" "$OFFICIAL" "${FILES[@]}" <<'PY'
import re, sys, os

FIX = sys.argv[1] == "1"
OFFICIAL = sys.argv[2] == "1"
FILES = sys.argv[3:]

CJK = r"\u4e00-\u9fff"
SKIP_UNIT = set("年月日天号时分秒个十百千万亿%℃°")  # 数字+这些单位之间不加空格
FENCE_RE = re.compile(r"^\s*(```|~~~)")

def is_cjk(ch):
    return '\u4e00' <= ch <= '\u9fff'

def mask_code(lines):
    """生成脱敏行: 代码围栏、行内代码、URL 替换为等长空格(保留行号)"""
    masked = []
    in_fence = False
    for ln in lines:
        if FENCE_RE.match(ln):
            in_fence = not in_fence
            masked.append(ln)
        elif in_fence:
            masked.append(" " * len(ln))
        else:
            ln2 = re.sub(r"`[^`\n]*`", lambda m: " " * len(m.group()), ln)
            ln2 = re.sub(r"https?://\S+", lambda m: " " * len(m.group()), ln2)
            ln2 = re.sub(r"\[[^\]]*\]\([^)]*\)", lambda m: " " * len(m.group()), ln2)
            masked.append(ln2)
    return masked

def find_issues(text, is_md):
    """返回 issues: (分类, 行号, 原文片段, 建议)"""
    lines = text.split("\n")
    masked = mask_code(lines) if is_md else lines
    issues = []

    # ---- 围栏内行号集合(结构/标题规则跳过代码块) ----
    fence_lines = set()
    if is_md:
        in_f = False
        for i, l in enumerate(lines):
            if FENCE_RE.match(l):
                in_f = not in_f
                continue
            if in_f:
                fence_lines.add(i)

    def add(cat, no, seg, sug):
        if seg:
            issues.append((cat, no + 1, seg.strip()[:60], sug))

    for i, ml in enumerate(masked):
        raw = lines[i]
        # ---- 行尾空格 ----
        m = re.search(r"[ \t]+$", raw)
        if m: add("空格", i, f"行尾有 {m.group(0).count(' ')+m.group(0).count(chr(9))} 个空格/制表符", "删除行尾空白")
        # ---- 制表符缩进 ----
        if raw.startswith("\t"): add("空格", i, "行首使用制表符", "改用空格缩进")
        # ---- 连续空行(>=3) ----
        # ---- 半角标点紧邻中文 ----
        for m in re.finditer(r"[,.;:!?][\u4e00-\u9fff]|[\u4e00-\u9fff][,.;:!?]", ml):
            ch = m.group()[0] if m.group()[0] in ",.;:!?" else m.group()[-1]
            full = {',':'，','.':'。',';':'；',':':'：','!':'！','?':'？'}[ch]
            add("标点", i, m.group(), f"中文语境用全角标点: {full}")
        # ---- 直引号贴中文 ----
        for m in re.finditer(r'"[\u4e00-\u9fff]|[\u4e00-\u9fff]"', ml):
            add("标点", i, m.group(), "中文语境使用弯引号 “ ”")
        # ---- 重复标点 ----
        for m in re.finditer(r"[！？。，；：]{2,}", ml):
            add("标点", i, m.group(), "不重复使用标点")
        # ---- 非标准省略号 ----
        for m in re.finditer(r"(\.{3,}|。{2,})", ml):
            add("标点", i, m.group(), "省略号用 ……")
        # ---- 全角数字/字母 ----
        for m in re.finditer(r"[Ａ-Ｚａ-ｚ０-９]+", ml):
            add("全半角", i, m.group(), "全角字符改为半角 " + "".join(
                chr(ord(c) - 0xFEE0) if 0xFF01 <= ord(c) <= 0xFF5E else c for c in m.group()))
        # ---- 半角括号贴中文 ----
        for m in re.finditer(r"\([\u4e00-\u9fff]|[\u4e00-\u9fff]\)", ml):
            add("标点", i, m.group(), "中文语境用全角括号 （）")
        # ---- 全角标点前/后空格 ----
        for m in re.finditer(r"[，。；：？！][ ]+", ml):
            add("空格", i, m.group(), "全角标点后不加空格")
        for m in re.finditer(r"[ ]+[，。；：？！]", ml):
            add("空格", i, m.group(), "全角标点前不加空格")
        # ---- 中文与英文之间缺空格 ----
        for m in re.finditer(rf"[\u4e00-\u9fff][A-Za-z]|[A-Za-z][\u4e00-\u9fff]", ml):
            add("空格", i, m.group(), "中英文之间加一个空格")
        # ---- 中文与数字之间缺空格(排除常用单位) ----
        for m in re.finditer(rf"[\u4e00-\u9fff][0-9]|[0-9][\u4e00-\u9fff]", ml):
            left_digit = m.group()[0].isdigit()
            cjk_ch = m.group()[0] if not left_digit else m.group()[1]
            if cjk_ch in SKIP_UNIT:
                continue
            add("空格", i, m.group(), "中文与数字之间加一个空格")
        # ---- 英文数字间多空格 ----
        for m in re.finditer(r"[A-Za-z0-9] {2,}[A-Za-z0-9]", ml):
            add("空格", i, m.group(), "只保留一个空格")
        # ---- Markdown: 标题末尾句末标点 ----
        if is_md and i not in fence_lines:
            mh = re.match(r"^\s*#{1,6}\s+(.*?)\s*#*\s*$", raw)
            if mh and re.search(r"[。．！？]$", mh.group(1)):
                add("Markdown", i, raw[:60], "标题末尾不加句末标点")

    # ---- 连续空行 ----
    blank_run = 0
    for i, raw in enumerate(lines):
        if raw.strip() == "":
            blank_run += 1
        else:
            if blank_run >= 3:
                add("空格", i - 1, f"连续 {blank_run} 个空行", "空行最多保留 2 个")
            blank_run = 0

    if is_md:
        # ---- 标题层级跳跃 MD001 ----
        heads = [(i, len(re.match(r"^#{1,6}", l).group())) for i, l in enumerate(lines)
                 if i not in fence_lines and re.match(r"^#{1,6}\s", l)]
        for k in range(1, len(heads)):
            if heads[k][1] - heads[k-1][1] > 1:
                add("Markdown", heads[k][0], lines[heads[k][0]][:60],
                    f"标题层级跳跃: #{'#'*heads[k-1][1]} → #{'#'*heads[k][1]}，不应跳级")
        # ---- 多个 H1 MD025 ----
        h1 = [i for i, l in enumerate(lines) if i not in fence_lines and re.match(r"^#\s", l)]
        if len(h1) > 1:
            add("Markdown", h1[1], lines[h1[1]][:60], f"文档只能有 1 个一级标题(共 {len(h1)} 个)")
        # ---- 标题前后空行 MD022 ----
        for i, l in enumerate(lines):
            if i in fence_lines or not re.match(r"^#{1,6}\s", l):
                continue
            if i > 0 and lines[i-1].strip() and not re.match(r"^#{1,6}\s|^---\s*$", lines[i-1]):
                add("Markdown", i, l[:60], "标题前需空一行")
            if i < len(lines)-1 and lines[i+1].strip() and not re.match(
                    r"^#{1,6}\s|^[-*]\s|^>\s|^```|^---\s*$", lines[i+1]):
                add("Markdown", i, l[:60], "标题后需空一行")
        # ---- 列表标记混用 MD004 ----
        prev_marker = None
        for i, l in enumerate(lines):
            if i in fence_lines:
                prev_marker = None; continue
            m = re.match(r"^(\s*)([-*+]) ", l)
            if m:
                if prev_marker is not None and m.group(2) != prev_marker and m.group(1) == prev_indent:
                    add("Markdown", i, l[:60], f"同级列表混用标记 {prev_marker}/{m.group(2)}，统一用一种")
                prev_marker = m.group(2); prev_indent = m.group(1)
            elif l.strip() and not l.startswith((" ", ">")):
                prev_marker = None
        # ---- 代码块无语言 MD040 ----
        in_f = False
        for i, l in enumerate(lines):
            m = FENCE_RE.match(l)
            if m:
                if not in_f and not l[m.end():].strip():
                    add("Markdown", i, l[:60], "代码块围栏后标注语言, 如 ```python")
                in_f = not in_f
        # ---- 行内代码/链接为空 ----
        for i, l in enumerate(lines):
            for m in re.finditer(r"(?<!`)``(?!`)|\[[^\]]*\]\(\)", l):
                add("Markdown", i, m.group(), "空行内代码/空链接地址")
            for m in re.finditer(r"!\[[^\]]*\]\([^)]*\)", l):
                if re.match(r"!\[[^\]]*\]\(\s*\)", m.group()):
                    add("Markdown", i, m.group(), "图片地址为空")
        # ---- 裸 URL MD034 ----
        for i, l in enumerate(lines):
            if i in fence_lines or "http" not in l:
                continue
            if not re.match(r"^\s*[<>]|\[", l.strip()):
                for m in re.finditer(r"(?<!\[)https?://[^\s)>\]]+", l):
                    add("Markdown", i, m.group(), "裸 URL 用链接语法 [文字](url)")
        # ---- 超长行 MD013 ----
        for i, l in enumerate(lines):
            if len(l) > 140 and i not in fence_lines and not l.startswith(("```", "|", "    ", "\t")) and "```" not in l:
                add("Markdown", i, f"行 {i+1} 长度 {len(l)}", "建议 ≤120 字符, 拆分长句")

    if OFFICIAL:
        # ---- 公文: 成文日期数字 ----
        for i, l in enumerate(masked):
            m = re.search(r"[\u4e00-\u9fff\u3007]{1,4}年[\u4e00-\u9fff\u3007]{1,2}月[\u4e00-\u9fff\u3007]{1,3}日", l)
            if m: add("公文", i, m.group(), "成文日期用阿拉伯数字, 如 2026年8月15日")
        fulltext = "\n".join(lines)
        if re.search(r"〔|〕", fulltext) is None and re.search(r"[\u4e00-\u9fff]+发?\s*\d+\s*号", fulltext):
            add("公文", 0, "含发文字号样式", "发文字号格式: 机关代字〔年份〕序号, 如 国办发〔2026〕1号")
    return issues

CAT_ORDER = ["标点", "空格", "全半角", "Markdown", "公文"]
CAT_CN = {"标点": "标点符号", "空格": "空格", "全半角": "全半角字符", "Markdown": "Markdown 结构", "公文": "公文格式"}

def fix_text(text, is_md):
    """安全自动修复"""
    lines = text.split("\n")
    masked = mask_code(lines) if is_md else lines
    fixed_lines = []
    for raw, ml in zip(lines, masked):
        ln = raw
        # 围栏内代码内容不修复
        if raw.strip() and ml.strip() == "":
            fixed_lines.append(ln); continue
        if is_md and re.match(r"^#{1,6}\s", ln):
            fixed_lines.append(ln); continue
        # 行尾空格
        ln = re.sub(r"[ \t]+$", "", ln)
        # 全角标点前后空格
        ln = re.sub(r"([，。；：？！]) +", r"\1", ln)
        ln = re.sub(r" +([，。；：？！])", r"\1", ln)
        # 半角标点→全角(仅紧邻中文, 不动 URL 里的点)
        ln = re.sub(r"([\u4e00-\u9fff\u3007])[,.]", lambda m: m.group(1) + ("，" if m.group(0)[1]=="," else "。"), ln)
        ln = re.sub(r"[,](?=[\u4e00-\u9fff\u3007])", "，", ln)
        ln = re.sub(r"[.](?=[\u4e00-\u9fff\u3007])", "。", ln)
        # 省略号
        ln = re.sub(r"\.{3,}|。{2,}", "……", ln)
        # 中英文/数字之间空格
        ln = re.sub(rf"([\u4e00-\u9fff])([A-Za-z])", r"\1 \2", ln)
        ln = re.sub(rf"([A-Za-z])([\u4e00-\u9fff])", r"\1 \2", ln)
        ln = re.sub(rf"([\u4e00-\u9fff])([0-9])", lambda m: m.group(1) + m.group(2) if m.group(2) in SKIP_UNIT else m.group(1)+" "+m.group(2), ln)
        ln = re.sub(rf"([0-9])([\u4e00-\u9fff])", lambda m: m.group(1)+m.group(2) if m.group(2) in SKIP_UNIT else m.group(1)+" "+m.group(2), ln)
        # 全角数字字母→半角
        ln = re.sub(r"[Ａ-Ｚａ-ｚ０-９]", lambda m: chr(ord(m.group())-0xFEE0), ln)
        # 多空格合并(英文之间)
        ln = re.sub(r"([A-Za-z0-9]) {2,}([A-Za-z0-9])", r"\1 \2", ln)
        fixed_lines.append(ln)
    out = "\n".join(fixed_lines)
    out = re.sub(r"\n{3,}", "\n\n", out)
    return out

total = 0
for fpath in FILES:
    with open(fpath, encoding="utf-8", errors="replace") as fh:
        text = fh.read()
    is_md = fpath.endswith((".md", ".markdown", ".mdx"))
    issues = find_issues(text, is_md)
    total += len(issues)
    kind = "Markdown（含结构检查）" if is_md else "纯文本"
    print(f"\n{'═'*50}")
    print(f"📄 {fpath}  —  {len(text.splitlines())} 行  · 类型: {kind}")
    if not issues:
        print("✅ 未发现问题，排版规范。")
        continue
    by_cat = {}
    for cat in CAT_ORDER:
        by_cat[cat] = [it for it in issues if it[0] == cat]
    for cat in CAT_ORDER:
        items = by_cat[cat]
        if not items: continue
        print(f"\n【{CAT_CN[cat]}】共 {len(items)} 处")
        for c, no, seg, sug in items[:40]:
            print(f"  L{no:>4}  「{seg}」  →  {sug}")
        if len(items) > 40:
            print(f"  … 其余 {len(items)-40} 处略")
    print(f"\n共 {len(issues)} 个问题。" + ("建议运行 --fix 自动修复安全项。" if not FIX else ""))

    if FIX and issues:
        new = fix_text(text, is_md)
        if new != text:
            bak = fpath + ".bak"
            with open(bak, "w", encoding="utf-8") as fh: fh.write(text)
            with open(fpath, "w", encoding="utf-8") as fh: fh.write(new)
            print(f"🔧 已自动修复 → 原文件备份: {bak}")

    if OFFICIAL and issues:
        pass

if OFFICIAL:
    print(f"\n{'═'*50}")
    print("📜 公文格式专项 (GB/T 9704-2012) — Word 版面核查清单:")
    print("""  A4纸; 天头(上白边)37mm±1; 订口(左白边)28mm±1; 版心156×225mm
  标题: 2号小标宋体, 居中, 回行梯形排布
  正文: 3号仿宋体; 一级标题黑体3号; 二级标题楷体3号; 三级标题仿宋加粗
  首行缩进2字符; 行距一般28~30磅
  页码: 4号半角宋体数字, 一字线, 版心下边缘之下7mm; 单页码居右空一字, 双页码居左空一字
  成文日期: 阿拉伯数字(2026年8月15日); 附件与正文一起装订页码连续
  版头: 份号/密级/紧急程度/发文机关标志/发文字号(机关代字〔年份〕序号)/签发人/分隔线
  版记: 抄送机关、印发机关和印发日期, 页末, 分隔线上下各一条""")

sys.exit(1 if total else 0)
PY
