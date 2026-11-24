# 宝宝穿衣助手 BabyWeather

根据当天天气，给 **2021 年出生（约 5 岁，幼儿园大班）** 的孩子生成当天穿衣建议的安卓 App。

## 功能

- 打开自动获取当天天气，可手动刷新
- 城市：手机定位，或手动选择 22 个城市（默认上海）
- 天气源：[Open-Meteo](https://open-meteo.com/) 免费公开接口，无需注册、无需 API Key
- 参考指标：今日最高/最低温、当前体感温度、降雨概率、风速、湿度、紫外线指数
- 建议分项输出：上衣 / 下装 / 外套 / 鞋袜 / 出门提醒
- 特殊提醒：温差大时洋葱穿法、下雨带雨具、风大防风、紫外线强防晒、天热备水和替换衣物

穿衣原则：5 岁孩子活动量大，一般比大人少穿一件或穿一样多；摸后背/后颈，温热不出汗就是刚好。

## 安装

- Android 7.0（API 24）以上
- 构建产物 `BabyWeather.apk`（调试证书签名，自用安装；不能上架应用商店）

## 构建

环境：JDK 17 + Android SDK platform 34 / build-tools 34.0.0（SDK 需放在 `~/android-sdk`，或改 `build.sh` 里的 `ANDROID_HOME`）。

```bash
bash build.sh
# 产物：BabyWeather.apk（已 zipalign + 签名，apksigner verify 通过）
```

无 Gradle、无第三方依赖：`build.sh` 用 aapt2 + javac + d8 手工打包。

## 结构

- `app/src/main/java/com/kalo/babyweather/MainActivity.java` — 全部界面、天气请求与穿衣建议逻辑（原生 Android，无第三方库）
- `app/src/main/AndroidManifest.xml`
- `build.sh` — 构建脚本

## v1.1 更新

- 新增卡通宝宝形象：推荐的衣服直接穿在宝宝身上（外套/内搭/裤子/鞋/帽子/围巾/雨伞随天气变化）
- 每一件写具体：颜色、款式、面料、厚度、尺码（120码/鞋28–29码参考），并注明"图上这件"
- 点宝宝图全屏放大，放大后双指捏合缩放、拖动看细节
- 每件单品带淘宝搜索链接（带尺码关键词，直达实时在售搜索结果；不用易失效的单品链接）
