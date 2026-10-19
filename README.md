<div align="center">

# OgAuto

**Telefon ekranını, telefon uygulamalarını ve IPTV / video yayınlarını Android Auto ekranına taşır.**<br>
Kablolu ve kablosuz Android Auto · root gerekmez · ücretsiz

<br>

<a href="https://github.com/OnurGuner5728/og-auto/raw/main/downloads/OgAuto-latest.apk"><img src="https://img.shields.io/badge/APK%20%C4%B0NDIR-OgAuto%20(en%20yeni)-1a73e8?style=for-the-badge&logo=android&logoColor=white" height="56" alt="APK indir"></a>
&nbsp;&nbsp;
<a href="https://www.patreon.com/c/onurguner"><img src="https://img.shields.io/badge/%E2%9D%A4%20BA%C4%9EI%C5%9E%20YAP-Patreon-f96854?style=for-the-badge&logo=patreon&logoColor=white" height="56" alt="Patreon ile bağış yap"></a>

<br><br>

<table><tr><td align="center">
☕ <b>OgAuto tamamen ücretsiz ve reklamsız.</b><br>
İşine yaradıysa <a href="https://www.patreon.com/c/onurguner"><b>Patreon’dan küçük bir destek</b></a> yeni özellikleri ve araç testlerini mümkün kılıyor. Teşekkürler!
</td></tr></table>

</div>

| Araçta | Ne yapar |
|---|---|
| **Yansıtma** | Telefon ekranını araçta yatay ve tam ekran gösterir; araç ekranından dokunma, kaydırma, hızlı kaydırma, iki parmakla yakınlaştırma, Geri / Ana ekran / Son uygulamalar. |
| **Uygulamalar** | Telefondaki uygulamaları araç ızgarasında listeler; seçilen uygulamayı telefonda açar. Araçta telefon ekranını görmek için ayrıca Yansıtmayı aç seçilir. |
| **Video / IPTV** | Yayını ekran yansıtması olmadan doğrudan araç ekranında oynatır (VYPN gibi uygulamalardan “harici oynatıcı”, paylaş veya M3U listesi). Yansıtmada video donması bu yolda yaşanmaz. |
| **VYPN** | VYPN'i telefonda açar ve yansıtma sayfasını gösterir, ancak ekran paylaşımını kendiliğinden başlatmaz. Medya önceki/sonraki komutları VYPN tarafından destekleniyorsa çalışır. |
| **Tarayıcı / YouTube TV** | Araç ekranında web sayfası ve YouTube TV arayüzü. |

> Sürücü sürüş sırasında kullanmamalıdır. Netflix, Disney+, Prime Video gibi DRM korumalı servisler araçta görünmez; bu uygulama DRM korumasını aşmaz.

---

## Kurulum (sırayla)

Görseller adımları anlatan **temsili çizimlerdir**; menü adları telefon markasına ve Android sürümüne göre biraz farklı olabilir. Uygulamanın içinde de aynı adımlar numaralı liste olarak durur; her adımın düğmesi doğrudan ilgili ayar sayfasını açar ve tamamlanan adımlar ✓ olur.

### 1. APK’yı indir
<img src="docs/guide/01-download.png" width="300" alt="APK indirme">

GitHub’da `downloads` klasörünü aç, en yeni `OgAuto-…apk` dosyasına dokun ve indir. Daha önce farklı imzalı bir OgAuto kuruluysa önce onu kaldır.

### 2. Play Protect taramasını kapat
<img src="docs/guide/02-play-protect.png" width="300" alt="Play Protect">

Play Protect'i normalde **açık bırak**. Uyarı çıkarsa nedeni incele; APK'yı yalnızca güvenilir kaynaktan yükle. Play Protect'i kapatmak zorunlu değildir.

### 3. APK’yı yükle
<img src="docs/guide/03-install-apk.png" width="300" alt="APK yükleme">

İndirilenler’de APK’ya dokun. İstenirse **“Bu kaynaktan izin ver”**i aç, geri dön ve **Yükle** de. “Güvenli olmayan uygulama” uyarısı çıkarsa **Ayrıntılar → Yine de yükle**.

### 4. OgAuto’yu aç ve onayla
<img src="docs/guide/04-open-consent.png" width="300" alt="Kullanım onayı">

Kullanım onayını kabul et. Ana ekranda **“Kurulum adımları”** listesi görünür.

### 5. Uygulamadaki adımları sırayla tamamla
<img src="docs/guide/05-checklist.png" width="300" alt="Kurulum listesi">

✗ işaretli her adımın altındaki düğmeye bas, ayarı yap, geri dön. Liste kendini günceller.

### 6. Android Auto için kur (Play yükleyici)
<img src="docs/guide/06-aa-install.png" width="300" alt="Android Auto için kur">

**“Android Auto için kur”** düğmesi OgAuto’yu, kurulum kaynağı Play Store olarak kaydedilecek şekilde yeniden kurar (KingInstaller’ın yaptığı işlem). Sistem ekranında **Güncelle** de. OgAuto kapanır; yeniden aç. Satırda ✓ görünmüyorsa telefon bu yöntemi kabul etmiyordur; 12–14. adımlar yine de OgAuto’yu Android Auto’da gösterir.

### 7. Kısıtlanmış ayarlara izin ver (Android 13 ve sonrası)
<img src="docs/guide/07-restricted.png" width="300" alt="Kısıtlanmış ayarlar">

**Uygulama bilgisini aç** → sağ üst **⋮** → **“Kısıtlanmış ayarlara izin ver”** → kilit/parmak izi ile onayla. Menü görünmüyorsa önce 8. adımı bir kez dene (sistem engeller), sonra buraya dön; seçenek artık görünür.

### 8. Dokunmatik servisini aç
<img src="docs/guide/08-accessibility.png" width="300" alt="Erişilebilirlik">

Erişilebilirlik → Yüklü uygulamalar → **OgAuto** → **Aç**. Araç ekranından telefona dokunma ve kaydırma bununla çalışır.

### 9. Diğer uygulamaların üzerinde göster
<img src="docs/guide/09-overlay.png" width="300" alt="Üstte gösterme">

OgAuto için **aç**. Paylaşım boyunca telefonu yatay kilitler ve ekranı açık tutar.

### 10. Sistem ayarlarını değiştirme
<img src="docs/guide/10-write-settings.png" width="300" alt="Sistem ayarları">

OgAuto için **aç**. Paylaşımda parlaklığı düşürür ve otomatik döndürmeyi yataya sabitler; paylaşım bitince eski ayarlar geri gelir.

### 11. Pil kısıtlamasını kaldır
<img src="docs/guide/11-battery.png" width="300" alt="Pil">

**İzin ver / Kısıtlanmamış** seç. Telefon OgAuto’yu arka planda durdurursa yansıtma kopar. Bildirim iznini de ver.

### 12. Android Auto geliştirici modunu aç
<img src="docs/guide/12-aa-dev.png" width="300" alt="Android Auto sürüm">

Uygulamadaki **“Android Auto ayarlarını aç”** düğmesi → en alttaki **Sürüm**e 10 kez dokun → onayla. Sonra sağ üst **⋮ → Geliştirici ayarları**.

### 13. Bilinmeyen kaynakları aç
<img src="docs/guide/13-unknown-sources.png" width="300" alt="Bilinmeyen kaynaklar">

Geliştirici ayarlarında **“Bilinmeyen kaynaklar”** açık.

### 14. Başlatıcıda OgAuto’yu işaretle
<img src="docs/guide/14-launcher.png" width="300" alt="Başlatıcıyı özelleştir">

Android Auto → **Başlatıcıyı özelleştir** → **OgAuto** işaretli. Araca bağlandığında uygulama listesinde görünür.

### 15. İstersen paylaşımı başlat — “Tüm ekran”
<img src="docs/guide/15-share.png" width="300" alt="Ekran paylaşımı">

OgAuto’da **“Ekran paylaşımını başlat”** → sistem penceresinde **Başlat**. OgAuto her zaman tüm ekranı ister; “tek uygulama” seçeneği yayınların araçta son karede donmasına yol açıyordu.

### 16. Araçta kullan
<img src="docs/guide/16-car.png" width="520" alt="Araç ekranı">

Araçta OgAuto ana menüsünde **Uygulamalar** ilk sıradadır. Uygulama seçimi ekran paylaşımı istemez. Aracın ekranında telefon içeriğini görmek için **Yansıtma** ekranındaki yayın simgesine dokun ve telefonda onayla. Daha önce etkin bir paylaşım varsa onu kullanır. Yansıtmada görüntü takılırsa ↻ düğmesine bas. Ayarlar’da yatay kilit, ekranı doldur ve video çözücü seçenekleri var.

### 17. IPTV / VYPN yayınlarını araçta oynat
<img src="docs/guide/17-iptv.png" width="300" alt="Harici oynatıcı">

IPTV uygulamasının ayarlarında **Harici oynatıcı → OgAuto (araçta oynat)** seç; kanala bastığında yayın doğrudan araçta açılır. Bu seçenek yoksa yayın bağlantısını OgAuto ile **paylaş** ya da OgAuto’daki **M3U liste adresi** alanına listeni ekle. Araç donarsa araç Ayarlar’ında **Video çözücü → Yazılım** seç.

---

## Sorun giderme

- **Araç listesinde OgAuto yok:** 6, 12, 13, 14. adımlar; telefonu araçtan ayırıp yeniden bağla.
- **Dokunmatik çalışmıyor:** 7 ve 8. adımlar; Android bazen güncellemeden sonra erişilebilirliği kapatır.
- **Video açınca Android Auto donuyor:** Sırayla dene:
  1. Araç Ayarlar → **Yansıtma kare hızı** 30 (varsayılan); sürerse **24 fps**. Telefonu araçtan ayırıp yeniden bağla.
  2. Android Auto → **Geliştirici ayarları** (ayarların en altındaki “Sürüm”e 10 kez dokun, sonra sağ üst ⋮):
     - **Video Çözünürlüğü → 720p** seç; sürerse **480p**. (Satırın altında “Araba ve telefonun kimlik doğrulamasına izin ver” yazması Android Auto’nun çeviri hatasıdır; dokununca çözünürlük menüsü açılır.)
     - **Videoyu Kaydet**, **Ekran görüntüsünün dökümünü al**, **Sesi Kaydet**, **Mikrofon Girişini Kaydet** → **kapalı**. Bunlar hata ayıklama kayıtlarıdır, oturum boyunca dosyaya yazar ve telefonu yorar.
     - **Bilinmeyen kaynaklar** ve (kablosuz kullanıyorsan) **Kablosuz Android Auto** → **açık** kalsın.
  3. Değişikliklerden sonra telefonu araçtan ayırıp yeniden bağla.
- **Araçta yine donarsa — sebebi bulmak için:** Donduğu anda telefonda **Geliştirici seçenekleri → Hata raporu al** (son dakikaların tüm sistem kaydı). Ardından OgAuto → **Tanı kaydını paylaş**. Bu kayıt 5 saniyede bir telefonun ürettiği ve OgAuto’nun araca çizdiği kare sayısını, en uzun boşluğu ve telefonun ısınma durumunu tutar:
  - Donma sırasında `out` kare sayısı devam ediyorsa telefon görüntü göndermeye devam ediyor demektir; sorun Android Auto / araç ünitesi tarafındadır (çözünürlüğü düşür, kablolu bağlantı dene).
  - `in`/`out` sıfıra düşüyorsa veya `thermal` severe/critical ise telefon zorlanıyordur (kare hızını 24’e düşür, telefonu soğut, şarjdan çıkar).
- **Yansıtmada video donuyor:** Yayını 17. adımdaki Video modunda aç. Yansıtmada kalacaksan araçta ↻ ve video çözücüyü değiştir.
- **Siyah görüntü:** Uygulama güvenli ekran / DRM kullanıyordur; aşılmaz.
- **Hata kaydı:** Telefon geliştirici seçenekleri → USB hata ayıklama, sonra `adb logcat -b all > ogauto.log` ve kaydı destek adresine gönder.

## Destek

<a href="https://www.patreon.com/c/onurguner"><img src="https://img.shields.io/badge/%E2%9D%A4%20BA%C4%9EI%C5%9E%20YAP-Patreon-f96854?style=for-the-badge&logo=patreon&logoColor=white" height="48" alt="Patreon ile bağış yap"></a>

- ❤ **Bağış:** [patreon.com/c/onurguner](https://www.patreon.com/c/onurguner)
- ✉ **E-posta:** onurguner.bjk@gmail.com

Ekran kareleri uygulama tarafından kaydedilmez veya geliştiriciye gönderilmez. Web siteleri, yayın sunucuları ve diğer uygulamaların kendi ağ trafiği devam eder. Bu uygulama Google tarafından onaylı değildir; Android Auto’da görünmesi telefona, araca ve Android Auto sürümüne bağlıdır.

## Sürümler

- **0.8.3** — Daha önce `experiment/v2-all` dalında araçta test edilen V2 optimizasyonları ana sürüme taşındı: uygulama simgelerini arka planda hazırlama, dokunmatik hareketleri sıraya alma, GPU kare bütçesi, görünüm geçişleri ve yeniden bağlanma, IPTV çözücü kurtarma, favoriler, son uygulamalar, kamera sonrası ekran geri yükleme denemesi ve performans kayıtları. Android Auto / araç davranışı cihazdan cihaza değişebilir; tüm V2 deneyleri her araçta kesin çalışır anlamına gelmez. V3'ün uygulama gömme ve iki panel denemeleri bu sürüme dahil **değildir**.

- **0.8.2** — Uygulamalar varsayılan olarak ilk sırada. Uygulama açmak veya Android Auto'nun yeniden bağlanması yansıtmayı başlatmaz; paylaşım araçtan manuel başlatılır. Erişilebilirlik izni açık olsa bile servis bağlantısının kopması ayrı gösterilir, Xiaomi otomatik başlatma ayarları için kısayol eklendi. VYPN'de medya geri/ileri düğmeleri sunulur (VYPN destekliyorsa çalışır).

- **0.8.1** — ImageReader karelerini bekletmeden tüketme, gerçek başarılı kare sayımı, başarısız çizim tanısı ve kontrollü otomatik yeniden bağlanma (oturum başına en fazla iki kez). Android Auto üzerindeki gerçek görünme durumunu telefon tek başına doğrulayamaz; donma sürerse tanı kaydını paylaşın.
- **0.8.0** — Yansıtma tanı kaydı (kare giriş/çıkış, en uzun boşluk, ısınma) ve “Tanı kaydını paylaş” düğmesi.
- **0.7.0** — Yansıtmada video açılınca Android Auto’nun donmasına karşı kare hızı sınırı (varsayılan 30 fps) ve düşük çözünürlüklü yakalama; araç Ayarlar’ında 30 / 24 / sınırsız seçimi.
- **0.6.0** — Araç menüsünde YouTube’un yanına **VYPN** kısayolu: telefonda VYPN’i açıp yansıtmaya geçer.
- **0.5.0** — Uygulama içi numaralı kurulum listesi: Play Protect, bilinmeyen kaynaklar, kısıtlanmış ayarlar, erişilebilirlik, üstte gösterme, sistem ayarları, pil, bildirim, Android Auto ve telefon geliştirici ayarlarına doğrudan yönlendirme. Patreon/destek düğmeleri.
- **0.4.0** — Araçta uygulama başlatıcı (Screen2Auto tarzı), hızlı kaydırma, iki parmak yakınlaştırma, son uygulamalar; yansıtma boyunca ekran açık (Fermata yöntemi), sistem ayarıyla yatay kilit.
- **0.3.0** — Araçta yerel Video / IPTV oynatıcı (Media3): harici oynatıcı, paylaş, M3U; HLS/DASH/RTSP/TS/MP4; yazılım çözücü seçeneği.
- **0.2.0** — Otomatik yatay kilit + ekranı doldurma, her zaman tüm ekran paylaşımı, ↻ yeniden bağlan, uygulama içi “Android Auto için kur”.
- **0.1.0** — İlk sürüm.

## Geliştirme

JDK 17, Android SDK 36, Gradle wrapper: `./gradlew :app:testDebugUnitTest :app:assembleDebug`. Her push’ta GitHub Actions APK’yı derleyip `downloads/` klasörüne koyar. Sabit imza için repo secrets: `OGAUTO_KEYSTORE_B64`, `OGAUTO_STORE_PASSWORD`, `OGAUTO_KEY_ALIAS`, `OGAUTO_KEY_PASSWORD` (yoksa debug imzalı `-debug.apk` üretilir). İmza anahtarlarını asla repoya ekleme. Kılavuz görselleri: `python3 scripts/make_guide_images.py`.

[Plan](docs/OGAUTO-PLAN.md) · [Lisanslar ve kaynaklar](THIRD_PARTY.md) · MIT

### Erişilebilirlik, yön ve bölünmüş ekran

- Android erişilebilirlik izinlerini OgAuto kendi kendine veremez veya Android'in kapattığı izni açamaz. Xiaomi/POCO için otomatik başlatma ve pil kısıtlamalarını kontrol et; izin açık ama servis bağlı değilse uygulama bu durumu ayrıca belirtir.
- Yatay kilit yalnızca telefonun manuel ekran yansıtmasında kullanılır. Android Auto Uygulamalar ızgarası kendi arayüzünü kullanır; telefonu yan çevirmek bu ızgarayı iyileştirmez.
- Video / IPTV'de önceki/sonraki kanal ve kanal listesi OgAuto'nun M3U listesinde çalışır. VYPN'de medya önceki/sonraki tuşları ancak VYPN bunları kabul ediyorsa etkilidir.
- Google Haritalar + tam etkileşimli OgAuto yansıtmasını Android Auto üzerinde zorunlu olarak ikiye ayıran genel bir API yoktur. Dashboard/split layout kararını Android Auto verir; Google Maps'i üçüncü taraf uygulama yüzeyinin içine gömmek desteklenmez.
