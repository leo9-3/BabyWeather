package com.kalo.babyweather;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private TextView weatherView, adviceView, statusView, ageView;
    private Spinner citySpinner;
    private Button refreshBtn;
    private ExecutorService exec = Executors.newSingleThreadExecutor();
    private Handler main = new Handler(Looper.getMainLooper());

    // name, lat, lon  (index 0 = use location)
    private final String[] cityNames = {"📍 使用手机定位","北京","上海","天津","重庆","广州","深圳","杭州","南京","苏州","成都","武汉","西安","长沙","郑州","合肥","青岛","大连","厦门","昆明","哈尔滨","乌鲁木齐"};
    private final double[][] cityCoords = {{0,0},{39.90,116.40},{31.23,121.47},{39.13,117.20},{29.56,106.55},{23.13,113.26},{22.55,114.06},{30.27,120.15},{32.06,118.80},{31.30,120.58},{30.66,104.06},{30.59,114.31},{34.34,108.94},{28.23,112.94},{34.75,113.63},{31.86,117.28},{36.07,120.38},{38.91,121.61},{24.48,118.09},{25.04,102.71},{45.80,126.53},{43.83,87.62}};

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(36, 40, 36, 36);
        root.setBackgroundColor(Color.parseColor("#FFF8F0"));

        TextView title = new TextView(this);
        title.setText("👶 宝宝穿衣助手");
        title.setTextSize(26); title.setTextColor(Color.parseColor("#222222"));
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        ageView = new TextView(this);
        int age = Calendar.getInstance().get(Calendar.YEAR) - 2021;
        ageView.setText("2021年出生 · 现在约 " + age + " 岁（幼儿园大班阶段）\n按活动量大的学龄前孩子来建议，不按小婴儿捂穿");
        ageView.setTextSize(14); ageView.setTextColor(Color.parseColor("#666666"));
        ageView.setGravity(Gravity.CENTER); ageView.setPadding(0,12,0,20);
        root.addView(ageView);

        TextView cityLabel = new TextView(this); cityLabel.setText("选择城市："); cityLabel.setTextSize(15);
        root.addView(cityLabel);

        citySpinner = new Spinner(this);
        citySpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, cityNames));
        citySpinner.setSelection(2); // default Shanghai, user can change / use location
        root.addView(citySpinner);

        refreshBtn = new Button(this);
        refreshBtn.setText("🌤  获取今日天气并生成穿衣建议");
        refreshBtn.setTextSize(16);
        refreshBtn.setOnClickListener(v -> loadWeather());
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2); bp.setMargins(0,20,0,12); refreshBtn.setLayoutParams(bp);
        root.addView(refreshBtn);

        statusView = new TextView(this); statusView.setText("点上面的按钮，就会按今天的最高/最低温、体感、降雨和风来建议。"); statusView.setTextSize(13); statusView.setTextColor(Color.parseColor("#888888"));
        root.addView(statusView);

        weatherView = makeCard(root, "今日天气");
        adviceView = makeCard(root, "今日穿衣建议");

        TextView foot = new TextView(this);
        foot.setText("\n小原则：5岁孩子跑动多，一般比大人少穿一件或穿一样多就行；摸后背/后颈，温热不出汗就是刚好，凉了加一件，出汗了减一件。\n天气数据：Open-Meteo（免费公开天气，无需注册）");
        foot.setTextSize(12); foot.setTextColor(Color.parseColor("#999999"));
        root.addView(foot);

        ScrollView sv = new ScrollView(this); sv.addView(root);
        setContentView(sv);

        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }
        loadWeather();
    }

    private TextView makeCard(LinearLayout root, String heading) {
        TextView h = new TextView(this); h.setText("\n" + heading); h.setTextSize(18); h.setTextColor(Color.parseColor("#E67E22"));
        root.addView(h);
        TextView t = new TextView(this);
        t.setText("—"); t.setTextSize(16); t.setTextColor(Color.parseColor("#333333"));
        t.setPadding(24,20,24,20); t.setBackgroundColor(Color.WHITE); t.setLineSpacing(6,1.0f);
        root.addView(t);
        return t;
    }

    private void loadWeather() {
        int idx = citySpinner.getSelectedItemPosition();
        statusView.setText("正在获取天气…"); refreshBtn.setEnabled(false);
        exec.execute(() -> {
            try {
                double lat, lon; String city;
                if (idx == 0) {
                    double[] loc = getLocation();
                    if (loc == null) { lat = 31.23; lon = 121.47; city = "定位失败，暂用上海"; }
                    else { lat = loc[0]; lon = loc[1]; city = "当前位置"; }
                } else { lat = cityCoords[idx][0]; lon = cityCoords[idx][1]; city = cityNames[idx]; }
                String url = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon +
                    "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m" +
                    "&daily=temperature_2m_max,temperature_2m_min,weather_code,precipitation_probability_max,uv_index_max,wind_speed_10m_max" +
                    "&timezone=auto&forecast_days=1";
                String json = httpGet(url);
                JSONObject j = new JSONObject(json);
                JSONObject cur = j.getJSONObject("current");
                JSONObject daily = j.getJSONObject("daily");
                double tcur = cur.getDouble("temperature_2m");
                double feels = cur.getDouble("apparent_temperature");
                double hum = cur.getDouble("relative_humidity_2m");
                double wind = cur.getDouble("wind_speed_10m");
                int code = cur.getInt("weather_code");
                double tmax = daily.getJSONArray("temperature_2m_max").getDouble(0);
                double tmin = daily.getJSONArray("temperature_2m_min").getDouble(0);
                int precip = daily.getJSONArray("precipitation_probability_max").isNull(0) ? 0 : daily.getJSONArray("precipitation_probability_max").getInt(0);
                double uv = daily.getJSONArray("uv_index_max").isNull(0) ? 0 : daily.getJSONArray("uv_index_max").getDouble(0);
                double windMax = daily.getJSONArray("wind_speed_10m_max").getDouble(0);

                String weatherText = city + "\n" +
                    "现在：" + round(tcur) + "°C（体感 " + round(feels) + "°C） " + codeText(code) + "\n" +
                    "今日：" + round(tmin) + "°C ～ " + round(tmax) + "°C，温差 " + round(tmax-tmin) + "°C\n" +
                    "降雨概率最高：" + precip + "%   风：" + round(Math.max(wind, windMax)) + " km/h\n" +
                    "湿度：" + round(hum) + "%   紫外线指数最高：" + String.format("%.1f", uv);
                String advice = buildAdvice(tmin, tmax, feels, code, precip, Math.max(wind, windMax), uv);
                main.post(() -> { weatherView.setText(weatherText); adviceView.setText(advice); statusView.setText("已更新 · " + city); refreshBtn.setEnabled(true); });
            } catch (Exception e) {
                main.post(() -> { statusView.setText("获取失败，请检查网络后重试：" + e.getMessage()); refreshBtn.setEnabled(true); adviceView.setText(buildOfflineAdvice()); });
            }
        });
    }

    private String buildOfflineAdvice() {
        return "暂时没拿到天气，可以先按这个通用法则选：\n\n洋葱穿法：内层透气打底 + 中层保暖 + 外层挡风外套。\n早晚凉就三层都穿，中午热脱外层。\n出门前看手机自带天气的最低温选外套厚度，最高温选内搭。";
    }

    private String buildAdvice(double tmin, double tmax, double feels, int code, int precip, double wind, double uv) {
        StringBuilder s = new StringBuilder();
        // Use daytime max as main, morning min for jacket timing
        double day = tmax;
        String top, bottom, outer, shoes;
        if (day >= 28) {
            top = "短袖T恤 / 速干短袖（纯棉或速干，别穿长袖捂着）";
            bottom = "短裤 / 薄七分裤，女孩可穿薄裙+安全裤";
            outer = "不用外套；进空调房备一件薄防晒衣/空调衫就行";
            shoes = "透气运动鞋或凉鞋（幼儿园活动多，优先包头凉鞋），薄短袜";
        } else if (day >= 23) {
            top = "短袖T恤，外面可搭一件薄衬衫/防晒衣";
            bottom = "薄长裤 / 七分裤 / 短裤均可";
            outer = "早晚（" + round(tmin) + "°C）备薄外套一件，中午脱掉";
            shoes = "透气运动鞋 + 薄棉袜";
        } else if (day >= 18) {
            top = "长袖T恤 / 薄卫衣";
            bottom = "长裤（牛仔/运动裤）";
            outer = "薄夹克 / 牛仔外套，早晚穿、中午可脱";
            shoes = "运动鞋 + 棉袜";
        } else if (day >= 13) {
            top = "长袖打底 + 毛衣/卫衣";
            bottom = "长裤，风大时选稍厚的";
            outer = "夹克 / 风衣（挡风款）";
            shoes = "运动鞋 + 中厚棉袜";
        } else if (day >= 8) {
            top = "保暖打底 + 毛衣/抓绒";
            bottom = "加绒长裤 / 厚长裤";
            outer = "棉服 / 厚夹克";
            shoes = "保暖运动鞋 + 厚袜；备薄帽子";
        } else if (day >= 3) {
            top = "保暖内衣 + 毛衣/抓绒";
            bottom = "加绒裤 / 保暖裤+外裤";
            outer = "羽绒服 / 厚棉服";
            shoes = "保暖鞋靴 + 厚袜；帽子+手套建议带上";
        } else {
            top = "保暖内衣 + 厚毛衣/抓绒（多层）";
            bottom = "保暖裤 + 加绒外裤";
            outer = "长款/厚羽绒服";
            shoes = "雪地靴/保暖靴 + 厚袜；帽子+围巾+手套全套";
        }
        s.append("👕 上衣：").append(top).append("\n\n");
        s.append("👖 下装：").append(bottom).append("\n\n");
        s.append("🧥 外套：").append(outer).append("\n\n");
        s.append("👟 鞋袜：").append(shoes).append("\n");

        // Extras
        StringBuilder extra = new StringBuilder();
        boolean rainy = precip >= 40 || (code >= 51 && code <= 67) || (code >= 80 && code <= 82) || code >= 95;
        if (rainy) extra.append("• 有雨：带雨伞/儿童雨衣，穿雨靴或备一双替换袜子，裤脚别太长\n");
        if (code >= 71 && code <= 77 || code == 85 || code == 86) extra.append("• 有雪：穿防滑保暖靴，裤子塞进靴筒，手套必备\n");
        if (wind >= 30) extra.append("• 风大（").append(round(wind)).append(" km/h）：外套选防风款，戴帽子，别穿太宽松容易灌风的衣服\n");
        if (uv >= 6) extra.append("• 紫外线强：戴遮阳帽、涂儿童防晒，中午尽量阴凉处活动\n");
        else if (uv >= 3 && day >= 20) extra.append("• 紫外线中等：户外时间长的话戴帽子/涂防晒\n");
        if (tmax - tmin >= 8) extra.append("• 温差大（").append(round(tmax-tmin)).append("°C）：一定用洋葱穿法，书包里塞一件薄外套，早晚加、中午减\n");
        if (feels <= tmax - 4) extra.append("• 体感比实际冷不少（风/湿度原因），按上面建议再加一层挡风\n");
        if (day >= 26) extra.append("• 天热：带水杯多喝水，备一条汗巾/替换短袖，跑出汗后及时擦干换掉，别对着空调直吹\n");
        if (extra.length() > 0) s.append("\n🎒 出门提醒：\n").append(extra);
        s.append("\n✅ 出门前摸一下后背：温热不出汗 = 刚好；发凉加一件，潮湿出汗减一件。");
        return s.toString();
    }

    private String codeText(int c) {
        if (c == 0) return "☀️ 晴";
        if (c <= 2) return "🌤 多云/晴间多云";
        if (c == 3) return "☁️ 阴";
        if (c == 45 || c == 48) return "🌫 有雾";
        if (c >= 51 && c <= 57) return "🌦 毛毛雨";
        if (c >= 61 && c <= 67) return "🌧 下雨";
        if (c >= 71 && c <= 77) return "🌨 下雪";
        if (c >= 80 && c <= 82) return "🌧 阵雨";
        if (c == 85 || c == 86) return "🌨 阵雪";
        if (c >= 95) return "⛈ 雷雨";
        return "天气代码 " + c;
    }
    private String round(double v) { return String.valueOf(Math.round(v)); }

    private double[] getLocation() {
        try {
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) return null;
            if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null;
            Location loc = null;
            for (String p : lm.getProviders(true)) { Location l = lm.getLastKnownLocation(p); if (l != null && (loc == null || l.getTime() > loc.getTime())) loc = l; }
            if (loc == null) return null;
            return new double[]{loc.getLatitude(), loc.getLongitude()};
        } catch (Exception e) { return null; }
    }

    private String httpGet(String urlStr) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setConnectTimeout(10000); c.setReadTimeout(10000); c.setRequestProperty("User-Agent", "BabyWeather/1.0");
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(); String line; while ((line = r.readLine()) != null) sb.append(line);
        r.close(); return sb.toString();
    }
}
