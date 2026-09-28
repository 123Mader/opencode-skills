#!/data/data/com.termux/files/usr/bin/bash
# health-live: 医学健康信息实时检索（最新文献 + 全球统计 + 权威参考）
set -euo pipefail
export PYTHONIOENCODING=utf-8

usage() {
    cat <<'EOF'
用法: health.sh <症状/疾病关键词> [选项]
      health.sh --stats <国家/全球>

  关键词: 任意症状或疾病名，如 胸痛 / 高血压 / 胃癌 / 儿童发烧
  选项:
    --papers N   显示前 N 篇最新研究文献 (默认 5)
    --stats <国> 查询全球健康统计 (life-expectancy / diabetes-prevalence 等)
    -h, --help   帮助

示例:
  health.sh 胸痛
  health.sh 高血压 --papers 8
  health.sh --stats China
EOF
}

PAPERS=5
KW=""
STATS=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --papers) PAPERS="$2"; shift ;;
        --stats) STATS="$2"; shift ;;
        -h|--help) usage; exit 0 ;;
        *) KW="${KW:-}$1" ;;
    esac
    shift
done

command -v curl >/dev/null || { echo "错误: 需要 curl"; exit 1; }
command -v python3 >/dev/null || { echo "错误: 需要 python3"; exit 1; }

# ---------- 全球/国家统计 ----------
if [ -n "$STATS" ]; then
    echo "🌍 健康统计: $STATS (来源: Our World in Data)"
    for slug in life-expectancy "diabetes-prevalence"; do
        label=$(python3 -c "print('期望寿命(岁)' if '$slug'=='life-expectancy' else '糖尿病患病率(% 20-79岁)')")
        curl -sSL --max-time 20 "https://ourworldindata.org/grapher/$slug.csv" 2>/dev/null | python3 -c "
import sys,csv
target='$STATS'.lower()
rows=list(csv.DictReader(sys.stdin))
# 国家名匹配(中文/英文/别名)
alias={'china':'China','中国':'China','usa':'United States','美国':'United States','us':'United States',
'japan':'Japan','日本':'Japan','india':'India','印度':'India','uk':'United Kingdom','英国':'United Kingdom',
'germany':'Germany','德国':'Germany','france':'France','法国':'France'}
name=alias.get(target,target.title() if target not in rows[0].get('Entity','') else target)
best=[r for r in rows if r.get('Entity','').lower()==target or r.get('Entity')==name]
if not best:
    best=[r for r in rows if target in r.get('Entity','').lower()]
if best:
    r=max(best,key=lambda x:x.get('Year','0') or '0')
    print(f'  {r[\"Entity\"]} {r.get(\"Year\")}: {r[list(r.keys())[-1]]}')
else:
    print(f'  (未找到 {STATS} 的数据)')
"
    done
    echo "  更多: https://ourworldindata.org | WHO: https://www.who.int/data/gho"
    exit 0
fi

# ---------- 必填关键词 ----------
[ -z "$KW" ] && { echo "错误: 缺少关键词"; usage; exit 1; }

# ---------- 红旗征提醒 ----------
RED_FLAGS=(
    "胸痛:胸痛+出冷汗/呼吸困难/放射痛 → 立即急诊, 可能是心梗"
    "脑卒中|中风|偏瘫|口角歪斜|言语不清:FAST 征象(Face笑不出来/Arms双臂无力/Speech言语不清/Time立即就医)"
    "呕血|黑便|便血:消化道出血 → 急诊"
    "呼吸困难|气促:静息时气促 → 急诊"
    "意识障碍|昏迷|晕厥:立即急诊"
    "高热|发烧:婴幼儿发热≥39℃持续不退 / 成人高热伴皮疹或颈项强直 → 就医"
    "血尿|咯血|无痛性血尿:尽快就医排查"
    "消瘦|体重下降|不明原因:持续 >1月不明原因体重下降 → 就医排查"
    "头晕|头痛:突发剧烈头痛(雷击样) → 急诊"
    "癫痫|抽搐:立即急诊"
)
echo "⚠️  红旗征自查(如果符合请立即就医):"
hit=0
for f in "${RED_FLAGS[@]}"; do
    pat="${f%%:*}"
    if echo "$KW" | grep -qE "$pat"; then
        echo "  - ${f#*:}"
        hit=1
    fi
done
[ "$hit" = "0" ] && echo "  (当前关键词未命中已知急症征象; 若症状持续/加重请就医)"

# ---------- Europe PMC 最新文献 ----------
echo ""
echo "📚 最新研究文献 (Europe PMC, 全球 4500 万+ 篇):"
Q=$(python3 -c "import urllib.parse,sys;print(urllib.parse.quote(sys.argv[1]))" "$KW")
curl -sS --max-time 25 "https://www.ebi.ac.uk/europepmc/webservices/rest/search?query=$Q&format=json&pageSize=$PAPERS" | python3 -c "
import json,sys
try: d=json.load(sys.stdin)
except Exception: print('  检索失败,请稍后重试'); sys.exit()
n=d.get('hitCount',0)
print(f'  命中 {n:,} 篇文献, 最新 {len(d.get(\"resultList\",{}).get(\"result\",[]))} 篇:')
for r in d.get('resultList',{}).get('result',[]):
    jt=(r.get('journalTitle') or '')[:32]
    print(f'  - [{r.get(\"pubYear\",\"?\")}] {r.get(\"title\",\"\")[:72]}')
    print(f'      {jt}  PMID:{r.get(\"pmid\",\"-\")}  https://europepmc.org/article/med/{r.get(\"pmid\",\"\")}')
"

# ---------- 权威参考 ----------
echo ""
echo "🔗 权威参考(按需查阅):"
cat <<'EOF'
  全球: WHO who.int/data/gho | MSD 默沙东诊疗手册 msdmanuals.cn | Mayo Clinic mayoclinic.org
        NHS nhs.uk | CDC cdc.gov | PubMed pubmed.ncbi.nlm.nih.gov
  中国: 国家卫健委 nhc.gov.cn | 中国疾控中心 chinacdc.cn | 中国卫生健康统计年鉴(数据至2024)
        国家癌症中心 ncc.org.cn | 医脉通 medlive.cn | 丁香医生 dxy.com
  短视频: 抖音/小红书/快手/B站 搜"三甲医院官方号+科室"或"卫健委/疾控官方账号",
        拒绝: 卖药带货、唯一秘方、夸大疗效、无医疗资质认证的账号
EOF

# ---------- 免责声明 ----------
cat <<'EOF'

⚠️  重要声明:
  · 本工具仅汇总公开信息供参考，不构成医疗建议，不能替代医生诊断
  · 症状持续/加重、出现红旗征、或怀疑急症 → 立即就医 (中国急救电话 120)
  · 用药请遵医嘱，不要仅凭网络信息自行用药
EOF
