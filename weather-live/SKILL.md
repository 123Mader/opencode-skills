---
name: weather-live
description: 全球实时天气查询与卫星云图。用户说"今天天气""查天气""天气预报""卫星云图""空气质量""某地现在几度""实时天气"时使用。免 API key，纯 HTTP + curl/python3，Termux 离线脚本，无需注册任何服务。
---

# Weather Live — 全球实时天气

全球任意地点的**实时天气 + 7 天预报 + 空气质量 + 卫星云图**，全部免费、无需 API key、无需注册。数据来自 Open-Meteo（聚合 ECMWF / NOAA / DWD / JMA / 韩国气象厅等 30+ 国家气象模型）、挪威气象局 met.no（Meteosat 卫星图）、日本气象厅 Himawari-9。

## When to Use

- 用户问"现在几度 / 今天天气 / 未来天气 / 下雨吗 / 空气质量"
- 用户要卫星云图、雷达、实时气象观测
- 需要对比多个国家气象局数据的场景
- 离线环境（SSH/Termux）快速查天气

不需要查询具体地点天气时不要用（纯闲聊应忽略本技能）。

## 前置要求

- 依赖：`curl` `python3`（Termux 自带，无 jq 也可以）
- 无需任何 API key，全部免费接口
- 需要外网；网络失败会给出明确中文报错

## 用法

脚本位于 opencode 技能目录：

```bash
WX=~/.config/opencode/skills/weather-live/weather.sh
chmod +x "$WX"   # 首次执行一次

# 城市名（任意语言）
"$WX" 上海
"$WX" "New York"
"$WX" 巴黎 --sat

# 精确坐标 纬度,经度
"$WX" 31.23,121.47 --sat --full

# 选项
#   --sat    下载区域卫星云图到 ~/.cache/weather-live
#   --area   指定卫星区域: europe africa atlantic_ocean global mediterranean
#   --dir    自定义卫星图保存目录
#   --full   追加未来 24 小时逐 3 小时预报
#   -h       帮助
```

## 数据源

| 用途 | 服务 | 说明 |
|------|------|------|
| 预报 (7/16天) | Open-Meteo `api.open-meteo.com` | 30+ 国家气象模型自动 best-match，免 key，CC BY 4.0 |
| 地理编码 | Open-Meteo Geocoding `geocoding-api.open-meteo.com` | 城市名→坐标，支持中文/英文/任意语言 |
| 空气质量 | Open-Meteo Air Quality `air-quality-api.open-meteo.com` | US AQI / PM2.5 / PM10 |
| 历史天气 | Open-Meteo Archive `/v1/archive` | 1940 至今，ERA5 再分析 |
| 卫星云图(欧/非/大西洋) | met.no Geosatellite `api.met.no/weatherapi/geosatellite/1.4` | EUMETSAT Meteosat，每 10-60 分钟，CC BY 4.0，需 User-Agent |
| 卫星云图(亚太) | NICT Himawari Real-time `himawari8.nict.go.jp/img/D531106/latest.json` | 日本 Himawari-9 真彩色全盘 |
| 卫星云图(美洲) | RAMMB Slider / NASA GOES（网站，无公开直链图） | GOES-East/West 波段图 |

### 卫星区域自动规则（按经度）

| 经度 | 卫星源 | 自动下载 |
|------|--------|---------|
| -30° ~ 45°（欧洲/非洲/地中海） | Meteosat | ✅ 自动选 area=europe |
| ≥ 45°（亚太，含中国/日本/澳洲） | Himawari-9 | ✅ 全盘真彩色 |
| < -30°（美洲） | GOES | ❌ 仅给网站链接（RAMMB Slider 等） |

注：`--area global` 可拿 Meteosat 全球图（欧洲非洲方向，不含美洲）。美洲卫星图可访问 `https://weather.ndc.nasa.gov/cgi-bin/get-abi?satellite=GOESEastfullDiskband14&lat=0&lon=-78&zoom=4&width=800&height=600&quality=80` 人工构造。

## 国家气象局 / 卫星网站目录

- 中国：中央气象台 weather.cma.cn · 中国天气网 weather.com.cn · 中国气象数据网 data.cma.cn · 风云卫星 fy4.nsmc.org.cn
- 美国：NOAA/NWS weather.gov（API: api.weather.gov 免 key）· NOAA 卫星 nesdis.noaa.gov
- 日本：JMA jma.go.jp（API 免 key）· Himawari 云图 himawari8.nict.go.jp
- 欧洲：ECMWF ecmwf.int · EUMETSAT eumetsat.int（view.eumetsat.int 卫星视图）
- 英国 Met Office · 德国 DWD · 法国 Météo-France · 韩国 KMA · 澳洲 BOM · 挪威 yr.no（均免 key 公开数据）
- 综合：Windy.com · Zoom.Earth · Ventusky · wttr.in（终端）

## 输出约定

- **stdout**：主报告（当前天气 / 未来 7 天 / 空气质量 / 数据源链接，中文 + emoji）
- **stderr**：进度与错误信息；退出码 0=成功，非 0=失败
- `--sat` 时卫星图保存后打印路径，可直接 `catimg` 或推送到 app

## 常见问题

- `找不到城市「xx」` → 换大城市名，或直接给坐标 `纬度,经度`
- 预报 API 无响应 → 网络不通或限流，稍后重试
- 卫星图下载失败 → 该源可能临时不可用，换 `--area global` 或改经度测试
- 城市重名（如 Spring Field）→ geocoding 返回首个结果，可加国家后缀 "Springfield,US"
- 每小时多次调用 → Open-Meteo 免费额度 ~10000 次/天，正常使用无需担心

## 安全

- 全部 HTTPS，无 key、无账号、无跟踪
- 不写日志、不采集任何用户信息
- met.no 要求请求带 User-Agent（脚本已带），请勿修改
