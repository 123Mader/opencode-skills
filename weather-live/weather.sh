#!/data/data/com.termux/files/usr/bin/bash
# weather-live: 全球实时天气查询 + 卫星云图（免 key，纯 HTTP）
set -euo pipefail

SKILL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
UA="weather-live-skill/1.0 (opencode termux; non-commercial)"
TMPD="${TMPDIR:-/data/data/com.termux/files/usr/tmp}/opencode/weather"
SAVE_DIR="${HOME:-$PREFIX}/.cache/weather-live"
SAT=0
FULL=0
AREA_OPT=""

usage() {
    cat <<'EOF'
用法: weather.sh <城市|坐标> [选项]

  位置:   "上海" / "New York" / "巴黎" 任意语言城市名，或 "31.23,121.47" 坐标
  选项:
    --sat            下载一张区域卫星云图（Meteosat / Himawari-9）
    --area <区域>    指定卫星区域: europe africa atlantic_ocean global mediterranean (默认按位置自动)
    --dir <路径>     卫星图保存目录 (默认 ~/.cache/weather-live)
    --full           追加未来 24 小时逐小时预报
    -h, --help       本帮助

示例:
  weather.sh 上海
  weather.sh 上海 --sat
  weather.sh "Tokyo,JP" --sat --full
  weather.sh 34.773,113.722 --sat
EOF
}

log() { printf '%s\n' "$*" >&2; }

while [[ $# -gt 0 ]]; do
    case "$1" in
        --sat) SAT=1 ;;
        --full) FULL=1 ;;
        --area) AREA_OPT="$2"; shift ;;
        --dir) SAVE_DIR="$2"; shift ;;
        -h|--help) usage; exit 0 ;;
        -*) log "未知选项: $1"; usage; exit 1 ;;
        *) LOC="${LOC:-}$1" ;;
    esac
    shift
done

[ -z "${LOC:-}" ] && { log "错误: 缺少位置参数"; usage; exit 1; }

command -v curl >/dev/null || { log "错误: 需要 curl"; exit 1; }
command -v python3 >/dev/null || { log "错误: 需要 python3"; exit 1; }
mkdir -p "$TMPD" "$SAVE_DIR"

# ---------- 1. 地理编码 ----------
if [[ "$LOC" =~ ^-?[0-9]+(\.[0-9]+)?,-?[0-9]+(\.[0-9]+)?$ ]]; then
    LAT="${LOC%%,*}"; LON="${LOC##*,}"
    GEO_INFO="坐标 $LAT,$LON"
else
    Q="$(python3 -c "import urllib.parse,sys;print(urllib.parse.quote(sys.argv[1]))" "$LOC")"
    GEO=$(curl -sS --max-time 15 "https://geocoding-api.open-meteo.com/v1/search?name=$Q&count=3&language=zh&format=json" || true)
    GEO_PARSED=$(python3 - "$GEO" <<'PY'
import json,sys
raw=sys.argv[1]
try: d=json.loads(raw)
except Exception: d={}
res=d.get("results") or []
if not res:
    print(""); sys.exit()
r=res[0]
name=r.get("name","?"); country=r.get("country",""); admin=r.get("admin1","")
lng=r.get("language") or ""
parts=[p for p in [name,admin,country] if p]
print(f"{r['latitude']}|{r['longitude']}|{','.join(parts)}")
PY
)
    if [ -z "$GEO_PARSED" ]; then
        log "错误: 找不到城市「$LOC」。可试附近大城市名，或直接给坐标 纬度,经度 (如 31.23,121.47)"
        exit 1
    fi
    IFS='|' read -r LAT LON GEO_INFO <<<"$GEO_PARSED"
fi

log "定位: $GEO_INFO ($LAT, $LON)"

# ---------- 2. 预报 + 空气质量 ----------
FC=$(curl -sS --max-time 20 "https://api.open-meteo.com/v1/forecast?latitude=$LAT&longitude=$LON&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m,wind_direction_10m,precipitation,cloud_cover,surface_pressure&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,wind_speed_10m_max&timezone=auto&forecast_days=7")
[ -z "$FC" ] && { log "错误: 预报 API 无响应，检查网络"; exit 1; }
AQ=$(curl -sS --max-time 15 "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=$LAT&longitude=$LON&current=us_aqi,pm2_5,pm10" || true)

if [ "$FULL" = "1" ]; then
    FC_H=$(curl -sS --max-time 20 "https://api.open-meteo.com/v1/forecast?latitude=$LAT&longitude=$LON&hourly=temperature_2m,weather_code,precipitation_probability&forecast_hours=24&timezone=auto")
else
    FC_H=""
fi

# ---------- 3. 输出主报告 ----------
python3 - "$FC" "$AQ" "$FC_H" "$GEO_INFO" <<'PY'
import json,sys,datetime

fc_raw,aq_raw,fc_h_raw,geo=sys.argv[1:5]
try: fc=json.loads(fc_raw)
except Exception: fc={}
try: aq=json.loads(aq_raw)
except Exception: aq={}
try: fch=json.loads(fc_h_raw) if fc_h_raw else {}
except Exception: fch={}

WMO={
 0:"晴",1:"基本晴",2:"少云",3:"阴云",45:"雾",48:"雾凇",
 51:"毛毛雨",53:"小毛毛雨",55:"毛毛雨",56:"冻毛毛雨",57:"冻毛毛雨",
 61:"小雨",63:"中雨",65:"大雨",66:"冻雨",67:"冻雨",
 71:"小雪",73:"中雪",75:"大雪",77:"米雪",
 80:"阵雨",81:"中阵雨",82:"强阵雨",85:"阵雪",86:"阵雪",
 95:"雷暴",96:"雷暴伴冰雹",99:"雷暴伴冰雹"}
def wmo(c):
    c=int(c) if c is not None else -1
    return WMO.get(c, f"代码{c}")

def wind_dir(d):
    if d is None: return ""
    dirs=["北","东北","东","东南","南","西南","西","西北"]
    return dirs[int((d+22.5)//45)%8]+"风"

cur=fc.get("current") or {}
cu=fc.get("current_units") or {}
t=cur.get("time","") or ""
try:
    dt=datetime.datetime.fromisoformat(t.replace("Z","+00:00"))
    if dt.tzinfo:
        ts=f"{dt:%Y-%m-%d %H:%M} (UTC{dt.utcoffset().total_seconds()//3600:+.0f})"
    else:
        ts=f"{dt:%Y-%m-%d %H:%M}"
except Exception:
    ts=t
tz=fc.get("timezone_abbreviation") or ""

print(f"🌍 {geo}  —  {ts} {tz}")
print(f"   📍 {fc.get('latitude',0):.4f}, {fc.get('longitude',0):.4f}  海拔 {fc.get('elevation',0):.0f}m")

if cur:
    C=cu.get("temperature_2m","°C")
    temp=cur.get("temperature_2m")
    code=cur.get("weather_code")
    feels=cur.get("apparent_temperature")
    hum=cur.get("relative_humidity_2m")
    wind=cur.get("wind_speed_10m")
    wdir=cur.get("wind_direction_10m")
    prec=cur.get("precipitation")
    cloud=cur.get("cloud_cover")
    pres=cur.get("surface_pressure")
    line=f"   ☀️ {temp}{C} · {wmo(code)}"
    if feels is not None: line+=f" · 体感 {feels}{C}"
    if hum is not None: line+=f" · 湿度 {hum}%"
    if wind is not None: line+=f" · {wind_dir(wdir)}{wind}{cu.get('wind_speed_10m','km/h')}"
    if prec: line+=f" · 降水 {prec}{cu.get('precipitation','mm')}"
    if cloud is not None: line+=f" · 云量 {cloud}%"
    if pres is not None: line+=f" · 气压 {pres:.0f}hPa"
    print(line)

daily=fc.get("daily") or {}
if daily.get("time"):
    print("\n📅 未来 7 天:")
    for i,day in enumerate(daily["time"]):
        try:
            dd=datetime.date.fromisoformat(day)
            label=dd.strftime("%m/%d")
        except Exception:
            label=day
        tmax=daily.get("temperature_2m_max",[None]*7)[i]
        tmin=daily.get("temperature_2m_min",[None]*7)[i]
        code=daily.get("weather_code",[None]*7)[i]
        pop=daily.get("precipitation_probability_max",[None]*7)[i]
        wind=daily.get("wind_speed_10m_max",[None]*7)[i]
        s=f"   {label}  {wmo(code)}  {tmax}°/{tmin}°"
        if pop is not None: s+=f"  降雨概率 {pop}%"
        if wind is not None: s+=f"  风 {wind}km/h"
        print(s)

if fch and fch.get("hourly"):
    hh=fch["hourly"]
    t0=datetime.datetime.now().replace(minute=0,second=0,microsecond=0)
    print("\n⏱ 未来 24 小时 (逐3小时):")
    for i in range(0,24,3):
        try:
            ht=datetime.datetime.fromisoformat(hh["time"][i].replace("Z","+00:00"))
            hm=ht.astimezone().strftime("%H:%M")
        except Exception:
            hm=hh["time"][i][11:16]
        code=hh.get("weather_code",[None]*24)[i]
        temp=hh.get("temperature_2m",[None]*24)[i]
        pop=hh.get("precipitation_probability",[None]*24)[i]
        s=f"   {hm}  {temp}°C  {wmo(code)}"
        if pop is not None: s+=f"  降雨概率 {pop}%"
        print(s)

aq_cur=(aq.get("current") or {}) if aq else {}
if aq_cur.get("us_aqi") is not None:
    aqi=aq_cur["us_aqi"]
    lvl="优" if aqi<=50 else ("良" if aqi<=100 else ("轻度污染" if aqi<=150 else ("中度污染" if aqi<=200 else "重度污染")))
    s=f"\n🏭 空气质量 AQI(US): {aqi} ({lvl})"
    if aq_cur.get("pm2_5") is not None: s+=f" | PM2.5 {aq_cur['pm2_5']}μg/m³"
    if aq_cur.get("pm10") is not None: s+=f" | PM10 {aq_cur['pm10']}μg/m³"
    print(s)

if not cur and not daily.get("time"):
    print("\n⚠️ 预报数据为空，API 可能限流，稍后重试。")
PY

# ---------- 4. 卫星云图 ----------
sat_note=""
if [ "$SAT" = "1" ]; then
    # 经度区域规则
    ZONE=$(python3 -c "lon=float('$LON'); print('met' if -30<=lon<45 else ('him' if lon>=45 else 'goes'))")
    if [ -n "$AREA_OPT" ]; then
        AREA="$AREA_OPT"; SRC="Meteosat (EUMETSAT/挪威气象局 met.no)"
    elif [ "$ZONE" = "met" ]; then
        AREA="europe"; SRC="Meteosat (EUMETSAT/挪威气象局 met.no)"
    else
        AREA=""; SRC=""
    fi
    if [ -n "$AREA" ]; then
        IMG="$SAVE_DIR/sat_${AREA}_$(date +%Y%m%d_%H%M).png"
        log "下载卫星云图 (${SRC}) ..."
        curl -sS --max-time 30 -A "$UA" -o "$IMG" "https://api.met.no/weatherapi/geosatellite/1.4?area=$AREA" && {
            if python3 -c "import sys;d=open(sys.argv[1],'rb').read(8);sys.exit(0 if d[:4]==b'\x89PNG' else 1)" "$IMG"; then
                sat_note="🛰 ${SRC} · 区域=${AREA} → $IMG"
            else
                rm -f "$IMG"; sat_note="🛰 ${SRC} 下载失败"
            fi
        } || sat_note="🛰 ${SRC} 下载失败"
    else
        # 亚太 / 美洲
        if [ "$ZONE" = "him" ]; then
            log "下载 Himawari-9 卫星云图 (日本气象厅/NICT) ..."
            LATEST=$(curl -sS --max-time 15 "https://himawari8.nict.go.jp/img/D531106/latest.json" || echo "")
            T=$(python3 - <<PY
import json,sys,re
try:
    d=json.loads('''$LATEST''')
    s=d.get("date","")
    m=re.search(r"(\d{4})-(\d{2})-(\d{2}) (\d{2}):(\d{2})",s)
    print(f"{m.group(1)}/{m.group(2)}/{m.group(3)}/{m.group(4)}{m.group(5)}" if m else "")
except Exception:
    print("")
PY
)
            if [ -n "$T" ]; then
                IMG="$SAVE_DIR/himawari9_$(date +%Y%m%d_%H%M).png"
                if curl -sS --max-time 30 -o "$IMG" "https://himawari8.nict.go.jp/img/D531106/1d/550/${T}00_0_0.png" && python3 -c "import sys;d=open(sys.argv[1],'rb').read(8);sys.exit(0 if d[:4]==b'\x89PNG' else 1)" "$IMG"; then
                    sat_note="🛰 Himawari-9 真彩色全盘 (日本气象厅/NICT) → $IMG"
                else
                    rm -f "$IMG"; sat_note="🛰 Himawari-9 下载失败"
                fi
            else
                sat_note="🛰 Himawari-9 时间戳获取失败"
            fi
        else
            sat_note="🛰 GOES (美洲) 无直接下载通道,请访问 RAMMB Slider / NASA GOES"
        fi
    fi
fi

# ---------- 5. 网站目录 ----------
cat <<'EOF'

🔗 全球天气数据源（实时）:
  预报/历史:   Open-Meteo api.open-meteo.com (30+模型, 免key) | wttr.in
  美国:        NOAA/NWS weather.gov (API: api.weather.gov)
  欧洲:        ECMWF ecmwf.int | EUMETSAT eumetsat.int | Meteosat
  日本:        JMA jma.go.jp | Himawari-9 云图 himawari8.nict.go.jp
  中国:        中央气象台 weather.cma.cn | 中国天气网 weather.com.cn | 中国气象数据网 data.cma.cn
  英国:        Met Office metoffice.gov.uk | 德国: DWD dwd.de | 法国: Météo-France meteofrance.fr
  韩国:        KMA kma.go.kr | 澳大利亚: BOM bom.gov.au | 挪威: yr.no
  卫星云图:    RAMMB Slider rammb-slider.cira.colostate.edu | NASA GOES weather.ndc.nasa.gov
               卫星图API: api.met.no/weatherapi/geosatellite (Meteosat)
  综合平台:    Windy.com | Zoom.Earth | Ventusky | AccuWeather | Weather Underground
EOF
if [ -n "$sat_note" ]; then echo ""; echo "$sat_note"; fi
