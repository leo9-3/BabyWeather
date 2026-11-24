package com.kalo.babyweather;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MainActivity extends Activity {
    private TextView weatherView, adviceView, statusView, detailView;
    private Spinner citySpinner;
    private Button refreshBtn;
    private ImageView photoView;
    private int currentBand = 3;
    private LinearLayout shopLayout;
    private Outfit currentOutfit;
    private ExecutorServiceHolder execHolder = new ExecutorServiceHolder();
    private Handler main = new Handler(Looper.getMainLooper());

    static class ExecutorServiceHolder { java.util.concurrent.ExecutorService e = java.util.concurrent.Executors.newSingleThreadExecutor(); }

    private final String[] cityNames = {"📍 使用手机定位","北京","上海","天津","重庆","广州","深圳","杭州","南京","苏州","成都","武汉","西安","长沙","郑州","合肥","青岛","大连","厦门","昆明","哈尔滨","乌鲁木齐"};
    private final double[][] cityCoords = {{0,0},{39.90,116.40},{31.23,121.47},{39.13,117.20},{29.56,106.55},{23.13,113.26},{22.55,114.06},{30.27,120.15},{32.06,118.80},{31.30,120.58},{30.66,104.06},{30.59,114.31},{34.34,108.94},{28.23,112.94},{34.75,113.63},{31.86,117.28},{36.07,120.38},{38.91,121.61},{24.48,118.09},{25.04,102.71},{45.80,126.53},{43.83,87.62}};

    // ---------- data ----------
    static class Item { String label, spec, keyword; Item(String l,String s,String k){label=l;spec=s;keyword=k;} }
    static class Outfit {
        int band, topColor, outerColor, bottomColor, shoeColor;
        int sleeve = 1;      // 0 short, 1 long
        int pants = 2;       // 0 shorts, 1 cropped, 2 long, 3 thick
        boolean hasOuter=false, puffy=false, scarf=false, gloves=false, rainy=false;
        int hat = 0;         // 0 none, 1 sunhat, 2 cap, 3 winter hat
        String summary = "";
        List<Item> items = new ArrayList<>();
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(36, 40, 36, 36);
        root.setBackgroundColor(Color.parseColor("#FFF8F0"));

        TextView title = new TextView(this);
        title.setText("👶 宝宝穿衣助手");
        title.setTextSize(26); title.setTextColor(Color.parseColor("#222222")); title.setGravity(Gravity.CENTER);
        root.addView(title);

        int age = Calendar.getInstance().get(Calendar.YEAR) - 2021;
        TextView ageView = new TextView(this);
        ageView.setText("2021年出生 · 现在约 " + age + " 岁（幼儿园大班阶段）\n尺码参考：身高105–115cm多穿120码，鞋约28–29码（以实测身高脚长为准）");
        ageView.setTextSize(13); ageView.setTextColor(Color.parseColor("#666666")); ageView.setGravity(Gravity.CENTER); ageView.setPadding(0,12,0,20);
        root.addView(ageView);

        TextView cityLabel = new TextView(this); cityLabel.setText("选择城市："); cityLabel.setTextSize(15); root.addView(cityLabel);
        citySpinner = new Spinner(this);
        citySpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, cityNames));
        citySpinner.setSelection(2);
        root.addView(citySpinner);

        refreshBtn = new Button(this);
        refreshBtn.setText("🌤  获取今日天气并生成穿衣建议");
        refreshBtn.setTextSize(16);
        refreshBtn.setOnClickListener(v -> loadWeather());
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2); bp.setMargins(0,20,0,12); refreshBtn.setLayoutParams(bp);
        root.addView(refreshBtn);

        statusView = new TextView(this); statusView.setText("正在准备…"); statusView.setTextSize(13); statusView.setTextColor(Color.parseColor("#888888")); root.addView(statusView);

        weatherView = makeCard(root, "今日天气");

        // ---- visual baby ----
        TextView vh = new TextView(this); vh.setText("\n今日造型（穿在宝宝身上就是这样）"); vh.setTextSize(18); vh.setTextColor(Color.parseColor("#E67E22")); root.addView(vh);
        photoView = new ImageView(this);
        photoView.setBackgroundColor(Color.WHITE);
        photoView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        photoView.setAdjustViewBounds(true);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(-1, 1100); vp.setMargins(0,8,0,4); photoView.setLayoutParams(vp);
        root.addView(photoView);
        TextView hint = new TextView(this); hint.setText("👆 点一下照片可以放大，放大后双指捏合缩放、拖动看细节（写实模特图）"); hint.setTextSize(13); hint.setTextColor(Color.parseColor("#888888")); hint.setGravity(Gravity.CENTER); root.addView(hint);
        photoView.setOnClickListener(v -> showZoom());

        detailView = makeCard(root, "每一件具体是什么（照着买/照着穿）");
        adviceView = makeCard(root, "穿衣建议与提醒");

        TextView sh = new TextView(this); sh.setText("\n🛒 淘宝搜同款（点开直达搜索结果）"); sh.setTextSize(18); sh.setTextColor(Color.parseColor("#E67E22")); root.addView(sh);
        TextView sn = new TextView(this); sn.setText("给的是带尺码的搜索链接，不是单个商品链接 — 单品链接容易失效/下架，搜索结果是实时在售的，照关键词+120码挑就行。"); sn.setTextSize(12); sn.setTextColor(Color.parseColor("#999999")); root.addView(sn);
        shopLayout = new LinearLayout(this); shopLayout.setOrientation(LinearLayout.VERTICAL); root.addView(shopLayout);

        TextView foot = new TextView(this);
        foot.setText("\n小原则：5岁孩子跑动多，一般比大人少穿一件或穿一样多就行；摸后背/后颈，温热不出汗就是刚好。\n天气数据：Open-Meteo（免费公开天气，无需注册）");
        foot.setTextSize(12); foot.setTextColor(Color.parseColor("#999999")); root.addView(foot);

        ScrollView sv = new ScrollView(this); sv.addView(root); setContentView(sv);

        // placeholder outfit so the baby is dressed before weather loads
        applyOutfit(outfitFor(15, false, 0, 0, 3));

        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }
        loadWeather();
    }

    private TextView makeCard(LinearLayout root, String heading) {
        TextView h = new TextView(this); h.setText("\n" + heading); h.setTextSize(18); h.setTextColor(Color.parseColor("#E67E22")); root.addView(h);
        TextView t = new TextView(this); t.setText("—"); t.setTextSize(16); t.setTextColor(Color.parseColor("#333333"));
        t.setPadding(24,20,24,20); t.setBackgroundColor(Color.WHITE); t.setLineSpacing(6,1.0f); root.addView(t);
        return t;
    }

    private void showZoom() {
        if (currentOutfit == null) return;
        Dialog d = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        LinearLayout lay = new LinearLayout(this); lay.setOrientation(LinearLayout.VERTICAL); lay.setBackgroundColor(Color.WHITE); lay.setPadding(24,32,24,24);
        TextView h = new TextView(this); h.setText("今日造型放大图 — 双指缩放 / 拖动"); h.setTextSize(17); h.setGravity(Gravity.CENTER); lay.addView(h);
        TouchImageView big = new TouchImageView(this); big.setImageBitmap(loadBandBitmap(currentBand)); big.setBackgroundColor(Color.parseColor("#FFF8F0"));
        lay.addView(big, new LinearLayout.LayoutParams(-1, 0, 1f));
        TextView det = new TextView(this); det.setText(detailText(currentOutfit)); det.setTextSize(15); det.setPadding(16,16,16,16); lay.addView(det);
        Button close = new Button(this); close.setText("关闭"); close.setOnClickListener(v -> d.dismiss()); lay.addView(close);
        d.setContentView(lay); d.show();
    }

    // ---------- outfit logic ----------
    private Outfit outfitFor(double day, boolean rainy, double uv, double wind, double tmin) {
        Outfit o = new Outfit(); o.rainy = rainy;
        if (day >= 28) {
            o.band=0; o.sleeve=0; o.pants=0; o.hat = uv>=3?1:2; o.topColor=Color.parseColor("#FFD93D"); o.bottomColor=Color.parseColor("#4D96FF"); o.shoeColor=Color.parseColor("#B07D4F");
            o.summary="炎热天：短袖+短裤+凉鞋";
            o.items.add(new Item("上衣","黄色纯棉圆领短袖T恤，120码，95%棉+5%氨纶，薄款（约180g棉），胸前小汽车印花 — 图上这件","儿童纯棉短袖T恤 120码 薄款"));
            o.items.add(new Item("下装","蓝色五分短裤，120码，松紧腰牛仔/卡其，裤长到膝盖上方 — 图上这条","儿童五分短裤 120码 松紧腰"));
            o.items.add(new Item("鞋","棕色包头运动凉鞋，魔术贴款，约28码，鞋底防滑 — 图上这双","儿童包头凉鞋 魔术贴 28码"));
            o.items.add(new Item("帽子","宽檐遮阳帽，UPF50+，帽围约52cm — 图上这顶","儿童遮阳帽 UPF50 52cm"));
        } else if (day >= 23) {
            o.band=1; o.sleeve=0; o.pants=1; o.hat=2; o.topColor=Color.parseColor("#FF8A5C"); o.bottomColor=Color.parseColor("#6BCB77"); o.shoeColor=Color.parseColor("#FFFFFF");
            o.summary="暖和天：短袖+七分裤";
            o.items.add(new Item("上衣","橙色纯棉圆领短袖T恤，120码，薄款透气 — 图上这件","儿童纯棉短袖T恤 120码"));
            o.items.add(new Item("下装","绿色七分裤/薄长裤，120码，松紧腰，面料薄棉 — 图上这条","儿童七分裤 薄款 120码"));
            o.items.add(new Item("鞋","白色透气网面运动鞋，魔术贴，约28码 — 图上这双","儿童网面运动鞋 魔术贴 28码"));
            o.items.add(new Item("备用外套","早晚备薄防晒衣/空调衫一件（最低"+Math.round(tmin)+"°C时套上），白色或浅蓝","儿童防晒衣 薄款 120码"));
            o.items.add(new Item("帽子","蓝色鸭舌帽，帽围约52cm — 图上这顶","儿童鸭舌帽 52cm"));
        } else if (day >= 18) {
            o.band=2; o.sleeve=1; o.pants=2; o.topColor=Color.parseColor("#4D96FF"); o.bottomColor=Color.parseColor("#5B5B8A"); o.shoeColor=Color.parseColor("#FF6B6B");
            o.summary="舒适天：长袖T恤+长裤，备薄外套";
            o.items.add(new Item("上衣","蓝色纯棉长袖T恤，120码，中等厚度，圆领 — 图上这件","儿童纯棉长袖T恤 120码"));
            o.items.add(new Item("下装","深蓝长裤，120码，运动裤/软牛仔，松紧腰 — 图上这条","儿童长裤 运动裤 120码"));
            o.items.add(new Item("鞋","红色运动鞋，网面+魔术贴，约28码 — 图上这双","儿童运动鞋 魔术贴 28码"));
            o.items.add(new Item("备用外套","薄夹克一件，早晚穿、中午脱，藏青/牛仔蓝","儿童薄夹克 120码"));
        } else if (day >= 13) {
            o.band=3; o.sleeve=1; o.pants=2; o.hasOuter=true; o.topColor=Color.parseColor("#FFD93D"); o.outerColor=Color.parseColor("#4D96FF"); o.bottomColor=Color.parseColor("#44475A"); o.shoeColor=Color.parseColor("#333333");
            o.summary="凉天：卫衣/毛衣 + 夹克外套";
            o.items.add(new Item("内搭","黄色圆领卫衣，120码，加薄绒或毛圈款，纯棉 — 图上里层这件","儿童卫衣 圆领 120码"));
            o.items.add(new Item("外套","蓝色夹克/风衣，120码，防风面料，拉链款 — 图上外层这件","儿童夹克 防风 120码"));
            o.items.add(new Item("下装","深灰长裤，120码，稍厚运动裤/牛仔裤 — 图上这条","儿童长裤 120码 秋款"));
            o.items.add(new Item("鞋","黑色运动鞋，约28码，中厚棉袜 — 图上这双","儿童运动鞋 28码 秋"));
        } else if (day >= 8) {
            o.band=4; o.sleeve=1; o.pants=3; o.hasOuter=true; o.topColor=Color.parseColor("#E67E22"); o.outerColor=Color.parseColor("#C0392B"); o.bottomColor=Color.parseColor("#2C3E50"); o.shoeColor=Color.parseColor("#5D4037");
            o.summary="冷天：毛衣+厚外套/棉服";
            o.items.add(new Item("内搭","橙色毛衣/抓绒上衣，120码，圆领套头 — 图上里层这件","儿童毛衣 抓绒 120码"));
            o.items.add(new Item("外套","红色棉服/厚夹克，120码，填充棉款，连帽更挡风 — 图上外层这件","儿童棉服 连帽 120码"));
            o.items.add(new Item("下装","深蓝加绒长裤，120码，一体绒 — 图上这条","儿童加绒长裤 120码"));
            o.items.add(new Item("鞋","棕色保暖运动鞋/短靴，约28–29码，厚棉袜 — 图上这双","儿童保暖鞋 加绒 29码"));
        } else if (day >= 3) {
            o.band=5; o.sleeve=1; o.pants=3; o.hasOuter=true; o.puffy=true; o.hat=3; o.scarf=true; o.topColor=Color.parseColor("#9B59B6"); o.outerColor=Color.parseColor("#2471A3"); o.bottomColor=Color.parseColor("#1B2631"); o.shoeColor=Color.parseColor("#333333");
            o.summary="寒冷天：羽绒服+保暖多层";
            o.items.add(new Item("内搭","紫色保暖打底+毛衣，120码，打底选德绒/发热面料 — 图上里层","儿童保暖内衣套装 120码 德绒"));
            o.items.add(new Item("外套","蓝色羽绒服，120码，90%白鸭绒、中长款、连帽 — 图上这件横条纹羽绒服","儿童羽绒服 中长款 120码"));
            o.items.add(new Item("下装","黑色加绒保暖裤，120码，外裤+保暖裤两层 — 图上这条","儿童加绒裤 保暖 120码"));
            o.items.add(new Item("鞋帽","黑色保暖靴约29码 + 红色针织帽（帽围52cm）+ 围巾 — 图上这套","儿童雪地靴 29码 儿童针织帽 围巾"));
        } else {
            o.band=6; o.sleeve=1; o.pants=3; o.hasOuter=true; o.puffy=true; o.hat=3; o.scarf=true; o.gloves=true; o.topColor=Color.parseColor("#7D3C98"); o.outerColor=Color.parseColor("#B03A2E"); o.bottomColor=Color.parseColor("#17202A"); o.shoeColor=Color.parseColor("#222222");
            o.summary="严寒天：厚羽绒全套";
            o.items.add(new Item("内搭","保暖内衣+厚毛衣/抓绒，120码，两层 — 图上里层","儿童保暖内衣 厚毛衣 120码"));
            o.items.add(new Item("外套","红色长款厚羽绒服，120码，连帽、大填充 — 图上这件","儿童长款羽绒服 120码 加厚"));
            o.items.add(new Item("下装","加绒外裤+保暖裤，120码 — 图上这条","儿童加绒外裤 120码"));
            o.items.add(new Item("鞋帽手套","雪地靴约29码 + 针织帽 + 围巾 + 手套全套 — 图上这套","儿童雪地靴 手套 围巾 套装"));
        }
        o.items.add(0, underwearFor(o.band));
        if (rainy) o.items.add(new Item("雨具","儿童雨衣（书包位款）+ 雨伞，或雨靴一双，今天有雨必带","儿童雨衣 书包位 雨靴"));
        if (wind >= 30) o.items.add(new Item("防风","风大：外套拉链拉到顶，帽子戴紧，别穿宽松灌风款","儿童防风外套 120码"));
        return o;
    }

    private Item underwearFor(int band) {
        switch (band) {
            case 0: return new Item("内衣 · 第1层（最里层）","白色网眼薄棉小背心，120码，纯棉薄款，贴身吸汗；配浅色纯棉平角内裤一条 — 所有衣服都穿在它外面，领口露出的白边就是它","儿童背心 纯棉 内裤 120码 薄款");
            case 1: return new Item("内衣 · 第1层（最里层）","白色/浅色纯棉小背心，120码，薄款；配纯棉平角内裤一条 — 贴身层，短袖T恤穿在它外面","儿童纯棉背心 内裤套装 120码");
            case 2: return new Item("内衣 · 第1层（最里层）","纯棉薄打底衫/小背心，120码，圆领薄款；配纯棉平角内裤一条 — 长袖T恤穿在它外面","儿童纯棉打底衫 薄款 120码");
            case 3: return new Item("内衣 · 第1层（最里层）","纯棉秋衣，120码，薄款圆领打底；配纯棉内裤一条，秋裤/打底裤穿在长裤里面 — 卫衣穿在秋衣外面","儿童纯棉秋衣秋裤 120码 薄款");
            case 4: return new Item("内衣 · 第1层（最里层）","纯棉秋衣秋裤套装，120码，中厚款；秋裤是下装的第一层，外面再穿加绒长裤 — 毛衣穿在秋衣外面","儿童秋衣秋裤套装 120码 中厚");
            case 5: return new Item("内衣 · 第1层（最里层）","德绒保暖内衣套装（秋衣+秋裤），120码，自发热/德绒面料，贴身不扎 — 羽绒服里面的毛衣再穿在它外面","儿童德绒保暖内衣套装 120码");
            default: return new Item("内衣 · 第1层（最里层）","加绒/加厚保暖内衣套装（秋衣+秋裤），120码，最贴身一层 — 外面依次是毛衣、厚羽绒服、加绒外裤","儿童加绒保暖内衣套装 120码");
        }
    }

    private String detailText(Outfit o) {
        StringBuilder s = new StringBuilder();
        s.append("造型：").append(o.summary).append("\n");
        s.append("尺码参考：120码 / 鞋约28–29码（按孩子实际身高脚长）\n");
        s.append("穿的顺序：从里到外，先内衣，再内搭/上衣，再外套。\n\n");
        int n = 1;
        for (Item it : o.items) { s.append(n++).append(". ").append(it.label).append("：").append(it.spec).append("\n\n"); }
        return s.toString().trim();
    }

    private void applyOutfit(Outfit o) {
        currentOutfit = o;
        currentBand = o.band;
        photoView.setImageBitmap(loadBandBitmap(o.band));
        detailView.setText(detailText(o));
        shopLayout.removeAllViews();
        for (Item it : o.items) {
            if (it.keyword == null || it.keyword.isEmpty()) continue;
            Button btn = new Button(this);
            btn.setText("🛒 " + it.label + "：淘宝搜「" + it.keyword + "」");
            btn.setTextSize(14); btn.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            final String kw = it.keyword;
            btn.setOnClickListener(v -> {
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://s.taobao.com/search?q=" + Uri.encode(kw)))); }
                catch (Exception e) { Toast.makeText(this, "打不开淘宝，请检查网络/是否装了淘宝", Toast.LENGTH_SHORT).show(); }
            });
            shopLayout.addView(btn);
        }
    }

    // ---------- weather ----------
    private void loadWeather() {
        int idx = citySpinner.getSelectedItemPosition();
        statusView.setText("正在获取天气…"); refreshBtn.setEnabled(false);
        execHolder.e.execute(() -> {
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
                JSONObject j = new JSONObject(httpGet(url));
                JSONObject cur = j.getJSONObject("current"); JSONObject daily = j.getJSONObject("daily");
                double tcur = cur.getDouble("temperature_2m"), feels = cur.getDouble("apparent_temperature"), hum = cur.getDouble("relative_humidity_2m");
                int code = cur.getInt("weather_code");
                double tmax = daily.getJSONArray("temperature_2m_max").getDouble(0), tmin = daily.getJSONArray("temperature_2m_min").getDouble(0);
                int precip = daily.getJSONArray("precipitation_probability_max").isNull(0) ? 0 : daily.getJSONArray("precipitation_probability_max").getInt(0);
                double uv = daily.getJSONArray("uv_index_max").isNull(0) ? 0 : daily.getJSONArray("uv_index_max").getDouble(0);
                double windMax = daily.getJSONArray("wind_speed_10m_max").getDouble(0);
                boolean rainy = precip >= 40 || (code>=51&&code<=67) || (code>=80&&code<=82) || code>=95;
                String weatherText = city + "\n现在：" + Math.round(tcur) + "°C（体感 " + Math.round(feels) + "°C） " + codeText(code) + "\n今日：" + Math.round(tmin) + "°C ～ " + Math.round(tmax) + "°C，温差 " + Math.round(tmax-tmin) + "°C\n降雨概率最高：" + precip + "%   风：" + Math.round(windMax) + " km/h\n湿度：" + Math.round(hum) + "%   紫外线最高：" + String.format("%.1f", uv);
                String advice = buildAdvice(tmin, tmax, feels, code, precip, windMax, uv);
                Outfit outfit = outfitFor(tmax, rainy, uv, windMax, tmin);
                main.post(() -> { weatherView.setText(weatherText); adviceView.setText(advice); applyOutfit(outfit); statusView.setText("已更新 · " + city); refreshBtn.setEnabled(true); });
            } catch (Exception e) {
                main.post(() -> { statusView.setText("获取失败，请检查网络后重试：" + e.getMessage()); refreshBtn.setEnabled(true); });
            }
        });
    }

    private String buildAdvice(double tmin, double tmax, double feels, int code, int precip, double wind, double uv) {
        StringBuilder s = new StringBuilder();
        s.append(currentSummaryForTemp(tmax)).append("\n");
        boolean rainy = precip >= 40 || (code>=51&&code<=67) || (code>=80&&code<=82) || code>=95;
        if (rainy) s.append("\n• 有雨：带雨衣/雨伞，备一双替换袜子");
        if (code>=71&&code<=77||code==85||code==86) s.append("\n• 有雪：防滑保暖靴，手套必备");
        if (wind >= 30) s.append("\n• 风大：外套选防风款，戴帽子");
        if (uv >= 6) s.append("\n• 紫外线强：遮阳帽+儿童防晒，中午多在阴凉处");
        else if (uv >= 3 && tmax >= 20) s.append("\n• 紫外线中等：户外久了戴帽/涂防晒");
        if (tmax - tmin >= 8) s.append("\n• 温差大（").append(Math.round(tmax-tmin)).append("°C）：洋葱穿法，书包塞一件薄外套，早晚加、中午减");
        if (feels <= tmax - 4) s.append("\n• 体感明显更冷，按建议再加一层挡风");
        if (tmax >= 26) s.append("\n• 天热：带水杯，备汗巾和替换短袖，出汗及时擦干换掉");
        s.append("\n\n✅ 出门前摸后背：温热不出汗=刚好；发凉加一件，潮湿出汗减一件。");
        return s.toString();
    }
    private String currentSummaryForTemp(double day) {
        if (day>=28) return "今天热，按短袖短裤穿，重点防晒补水。";
        if (day>=23) return "今天暖和，短袖为主，早晚备薄外套。";
        if (day>=18) return "今天舒适，长袖+长裤，备薄外套应付早晚。";
        if (day>=13) return "今天偏凉，内搭+外套两层，裤子穿长裤。";
        if (day>=8)  return "今天冷，毛衣/抓绒+厚外套，下装加厚。";
        if (day>=3)  return "今天很冷，羽绒服为主，帽子围巾带上。";
        return "今天严寒，厚羽绒全套，帽子围巾手套齐全，尽量减少长时间户外。";
    }
    private String codeText(int c) {
        if (c==0) return "☀️ 晴"; if (c<=2) return "🌤 多云"; if (c==3) return "☁️ 阴";
        if (c==45||c==48) return "🌫 有雾"; if (c>=51&&c<=57) return "🌦 毛毛雨";
        if (c>=61&&c<=67) return "🌧 下雨"; if (c>=71&&c<=77) return "🌨 下雪";
        if (c>=80&&c<=82) return "🌧 阵雨"; if (c==85||c==86) return "🌨 阵雪"; if (c>=95) return "⛈ 雷雨"; return "";
    }
    private double[] getLocation() {
        try {
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (lm==null || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED) return null;
            Location best=null; for (String p: lm.getProviders(true)) { Location l=lm.getLastKnownLocation(p); if (l!=null&&(best==null||l.getTime()>best.getTime())) best=l; }
            return best==null?null:new double[]{best.getLatitude(), best.getLongitude()};
        } catch (Exception e) { return null; }
    }
    private String httpGet(String urlStr) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setConnectTimeout(10000); c.setReadTimeout(10000); c.setRequestProperty("User-Agent","BabyWeather/1.1");
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder sb=new StringBuilder(); String line; while((line=r.readLine())!=null) sb.append(line); r.close(); return sb.toString();
    }

    private android.graphics.Bitmap loadBandBitmap(int band) {
        try {
            java.io.InputStream is = getAssets().open("band" + band + ".jpg");
            android.graphics.Bitmap bm = android.graphics.BitmapFactory.decodeStream(is); is.close(); return bm;
        } catch (Exception e) { return null; }
    }

    static class TouchImageView extends ImageView {
        private android.graphics.Matrix matrix = new android.graphics.Matrix();
        private android.graphics.Matrix saved = new android.graphics.Matrix();
        private ScaleGestureDetector sgd; private float lastX, lastY; private boolean drag=false;
        TouchImageView(Context ctx){ super(ctx); setScaleType(ScaleType.MATRIX);
            sgd = new ScaleGestureDetector(ctx, new ScaleGestureDetector.SimpleOnScaleGestureListener(){
                @Override public boolean onScale(ScaleGestureDetector d){ matrix.postScale(d.getScaleFactor(), d.getScaleFactor(), d.getFocusX(), d.getFocusY()); setImageMatrix(matrix); return true; } });
        }
        @Override protected void onLayout(boolean ch,int l,int t,int r,int b){ super.onLayout(ch,l,t,r,b);
            if (getDrawable()!=null && matrix.isIdentity()){ float vw=getWidth(), vh=getHeight(), dw=getDrawable().getIntrinsicWidth(), dh=getDrawable().getIntrinsicHeight();
                float s=Math.min(vw/dw, vh/dh); matrix.setScale(s,s); matrix.postTranslate((vw-dw*s)/2f,(vh-dh*s)/2f); setImageMatrix(matrix); } }
        @Override public boolean onTouchEvent(MotionEvent e){ sgd.onTouchEvent(e);
            switch(e.getActionMasked()){ case MotionEvent.ACTION_DOWN: lastX=e.getX(); lastY=e.getY(); drag=true; break;
                case MotionEvent.ACTION_MOVE: if(drag && e.getPointerCount()==1){ matrix.postTranslate(e.getX()-lastX, e.getY()-lastY); lastX=e.getX(); lastY=e.getY(); setImageMatrix(matrix);} break;
                case MotionEvent.ACTION_UP: drag=false; break; }
            return true; }
    }

    // ---------- cartoon baby view (kept as fallback, not shown) ----------
    static class BabyView extends View {
        Outfit outfit; boolean zoomable=false; float zoom=1f, dx=0f, dy=0f;
        ScaleGestureDetector sgd; float lastX, lastY; boolean dragging=false;
        BabyView(Context ctx){ super(ctx); sgd=new ScaleGestureDetector(ctx, new ScaleGestureDetector.SimpleOnScaleGestureListener(){ @Override public boolean onScale(ScaleGestureDetector d){ zoom=Math.max(1f, Math.min(3.5f, zoom*d.getScaleFactor())); invalidate(); return true; } }); }
        void setOutfit(Outfit o){ outfit=o; invalidate(); }
        @Override public boolean onTouchEvent(MotionEvent e){
            if (!zoomable) return super.onTouchEvent(e);
            sgd.onTouchEvent(e);
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN: lastX=e.getX(); lastY=e.getY(); dragging=true; break;
                case MotionEvent.ACTION_MOVE: if(dragging && e.getPointerCount()==1 && zoom>1f){ dx+=e.getX()-lastX; dy+=e.getY()-lastY; lastX=e.getX(); lastY=e.getY(); invalidate(); } break;
                case MotionEvent.ACTION_UP: dragging=false; break;
            }
            return true;
        }
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            if (outfit==null) return;
            canvas.save();
            canvas.translate(dx, dy); canvas.scale(zoom, zoom, getWidth()/2f, getHeight()/2f);
            float W=getWidth(), H=getHeight();
            float s=Math.min(W/600f, H/820f); float ox=(W-600*s)/2f, oy=(H-820*s)/2f;
            canvas.translate(ox, oy); canvas.scale(s, s);
            drawBaby(canvas);
            canvas.restore();
        }
        private Paint p(int color){ Paint q=new Paint(Paint.ANTI_ALIAS_FLAG); q.setColor(color); q.setStyle(Paint.Style.FILL); return q; }
        private void drawBaby(Canvas c){
            Outfit o=outfit;
            int skin=Color.parseColor("#FFD9B3"), hair=Color.parseColor("#5D4037");
            // umbrella if rainy
            if (o.rainy){
                Paint up=p(Color.parseColor("#FF6B6B")); Path tri=new Path(); tri.moveTo(430,120); tri.lineTo(590,250); tri.lineTo(270,250); tri.close(); c.drawPath(tri, up);
                Paint lp=new Paint(Paint.ANTI_ALIAS_FLAG); lp.setColor(Color.DKGRAY); lp.setStrokeWidth(10); c.drawLine(430,120,430,430,lp);
            }
            // legs / pants
            if (o.pants==0){ // shorts + skin legs
                c.drawRect(225,520,295,600, p(o.bottomColor)); c.drawRect(305,520,375,600, p(o.bottomColor));
                c.drawRect(235,600,285,720, p(skin)); c.drawRect(315,600,365,720, p(skin));
            } else if (o.pants==1){
                c.drawRect(225,520,295,660, p(o.bottomColor)); c.drawRect(305,520,375,660, p(o.bottomColor));
                c.drawRect(235,660,285,720, p(skin)); c.drawRect(315,660,365,720, p(skin));
            } else {
                c.drawRect(225,520,295,725, p(o.bottomColor)); c.drawRect(305,520,375,725, p(o.bottomColor));
                if (o.pants==3){ Paint lp=new Paint(Paint.ANTI_ALIAS_FLAG); lp.setColor(Color.WHITE); lp.setStrokeWidth(4); c.drawLine(260,540,260,710,lp); c.drawLine(340,540,340,710,lp); }
            }
            // socks + shoes
            c.drawRect(232,720,288,742, p(Color.WHITE)); c.drawRect(312,720,368,742, p(Color.WHITE));
            boolean boots = o.band>=5 || o.band==4 && o.pants==3;
            if (o.band==0){ // sandals
                c.drawRoundRect(new RectF(215,742,295,775),12,12,p(o.shoeColor)); c.drawRoundRect(new RectF(305,742,385,775),12,12,p(o.shoeColor));
                c.drawRect(235,742,275,758,p(skin)); c.drawRect(325,742,365,758,p(skin));
            } else if (boots){
                c.drawRect(222,690,292,748, p(o.shoeColor)); c.drawRect(308,690,378,748, p(o.shoeColor));
                c.drawRoundRect(new RectF(215,742,300,778),12,12,p(o.shoeColor)); c.drawRoundRect(new RectF(300,742,385,778),12,12,p(o.shoeColor));
            } else {
                c.drawRoundRect(new RectF(215,738,300,778),14,14,p(o.shoeColor)); c.drawRoundRect(new RectF(300,738,385,778),14,14,p(o.shoeColor));
                Paint wp=new Paint(Paint.ANTI_ALIAS_FLAG); wp.setColor(Color.WHITE); wp.setStrokeWidth(5); c.drawLine(230,758,285,758,wp); c.drawLine(315,758,370,758,wp);
            }
            // arms (behind torso sides)
            int sleeveColor = o.hasOuter ? o.outerColor : o.topColor;
            if (o.sleeve==0){
                c.drawRect(165,285,220,360, p(o.topColor)); c.drawRect(380,285,435,360, p(o.topColor));
                c.drawRect(172,360,212,470, p(skin)); c.drawRect(388,360,428,470, p(skin));
            } else {
                c.drawRect(160,285,220,480, p(sleeveColor)); c.drawRect(380,285,440,480, p(sleeveColor));
            }
            // hands / gloves
            int handColor = o.gloves ? Color.parseColor("#B03A2E") : skin;
            c.drawCircle(190,492,24,p(handColor)); c.drawCircle(410,492,24,p(handColor));
            // undershirt collar peeking at neckline (layer 1)
            c.drawRoundRect(new RectF(252,248,348,282),14,14,p(Color.WHITE));
            // torso
            if (o.hasOuter){
                c.drawRoundRect(new RectF(205,270,395,540),36,36,p(o.outerColor));
                // inner top visible in middle
                Path inner=new Path(); inner.moveTo(262,272); inner.lineTo(338,272); inner.lineTo(322,540); inner.lineTo(278,540); inner.close(); c.drawPath(inner,p(o.topColor));
                Paint zp=new Paint(Paint.ANTI_ALIAS_FLAG); zp.setColor(Color.DKGRAY); zp.setStrokeWidth(4); c.drawLine(262,280,278,530,zp); c.drawLine(338,280,322,530,zp);
                if (o.puffy){ Paint lp=new Paint(Paint.ANTI_ALIAS_FLAG); lp.setColor(Color.argb(90,0,0,0)); lp.setStrokeWidth(5); for(int y=330;y<530;y+=48) c.drawLine(212,y,388,y,lp); }
            } else {
                c.drawRoundRect(new RectF(205,270,395,540),36,36,p(o.topColor));
                // cute print
                c.drawCircle(300,400,26,p(Color.WHITE)); c.drawCircle(292,394,5,p(Color.BLACK)); c.drawCircle(308,394,5,p(Color.BLACK));
                Paint mp=new Paint(Paint.ANTI_ALIAS_FLAG); mp.setColor(Color.BLACK); mp.setStrokeWidth(4); mp.setStyle(Paint.Style.STROKE); c.drawArc(new RectF(288,398,312,418),15,150,false,mp);
            }
            // scarf
            if (o.scarf){ c.drawRoundRect(new RectF(238,238,362,282),18,18,p(Color.parseColor("#E74C3C"))); c.drawRect(330,282,362,360,p(Color.parseColor("#E74C3C"))); }
            // head
            c.drawCircle(300,165,88,p(skin));
            // hair (if no hat)
            if (o.hat==0){ Path hp=new Path(); hp.moveTo(212,165); hp.arcTo(new RectF(212,77,388,253),180,180); hp.close(); c.drawPath(hp,p(hair)); }
            // face
            c.drawCircle(268,170,8,p(Color.BLACK)); c.drawCircle(332,170,8,p(Color.BLACK));
            Paint blush=p(Color.argb(120,255,120,140)); c.drawCircle(238,195,14,blush); c.drawCircle(362,195,14,blush);
            Paint mp2=new Paint(Paint.ANTI_ALIAS_FLAG); mp2.setColor(Color.parseColor("#8D5524")); mp2.setStrokeWidth(5); mp2.setStyle(Paint.Style.STROKE); c.drawArc(new RectF(278,182,322,212),15,150,false,mp2);
            // hats
            if (o.hat==1){ // sun hat
                c.drawOval(new RectF(185,118,415,152),p(Color.parseColor("#F4D03F"))); Path dome=new Path(); dome.moveTo(232,122); dome.arcTo(new RectF(232,52,368,122),180,180); dome.close(); c.drawPath(dome,p(Color.parseColor("#F9E79F")));
            } else if (o.hat==2){ // cap
                Path dome=new Path(); dome.moveTo(222,132); dome.arcTo(new RectF(222,58,378,132),180,180); dome.close(); c.drawPath(dome,p(Color.parseColor("#2471A3")));
                c.drawRoundRect(new RectF(300,118,420,136),8,8,p(Color.parseColor("#2471A3")));
            } else if (o.hat==3){ // winter hat
                Path dome=new Path(); dome.moveTo(210,140); dome.arcTo(new RectF(210,50,390,140),180,180); dome.close(); c.drawPath(dome,p(Color.parseColor("#E74C3C")));
                c.drawRoundRect(new RectF(208,128,392,158),12,12,p(Color.parseColor("#C0392B"))); c.drawCircle(300,58,18,p(Color.WHITE));
            }
        }
    }
}
