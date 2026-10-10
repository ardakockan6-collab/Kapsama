# Kapsama 2.0

Android Studio ile `Kapsama20` klasörünü açın. Minimum Android sürümü API 28, derleme SDK'sı 37'dir.

## Kurulum

Android Studio'nun oluşturduğu `Kapsama20/local.properties` dosyasına aşağıdaki satırları ekleyin. Mevcut `sdk.dir` satırını koruyun.

```properties
supabase.url=https://PROJE.supabase.co
supabase.key=SUPABASE_PUBLISHABLE_KEY
```

`local.properties` Git'e eklenmez. Publishable/anon anahtarını kullanın; sunucuya özel service-role veya secret anahtarını mobil uygulamaya koymayın. Ayarlar eksikse proje derlenir ancak giriş ve Supabase işlemleri çalışmaz.

Gradle eşitlemesini tamamlayıp `app` yapılandırmasını çalıştırın. Komut satırından derlemek için bu klasörde `./gradlew :app:assembleDebug` kullanın (Windows PowerShell: `.\gradlew.bat :app:assembleDebug`).

## Öğrenci girişi

Öğrenci hesabı Supabase Authentication içinde önceden oluşturulmuş olmalıdır. Mobil uygulama e-posta ve şifreyle `POST /auth/v1/token?grant_type=password` çağrısını yapar.

Hesabın **app_metadata.ogrenci_id** alanı, `public.ogrenciler.id` değerine karşılık gelen metin kimliğini içermelidir. Bu alan yönetici tarafından ayarlanır; eksikse uygulama girişte hata gösterir. Auth kullanıcısının UUID'si yerine bu öğrenci kimliği kullanılır.

Görünen isim `user_metadata.full_name`, ardından `user_metadata.name` alanından alınır; ikisi de yoksa e-posta gösterilir. A/B/C öğrenci seçimi kaldırılmıştır.

Supabase REST istekleri `apikey` ve `Authorization: Bearer <access_token>` başlıklarını gönderir. Süresi dolan token `grant_type=refresh_token` ile yenilenir. REST çağrısı 401 dönerse token yenilenerek istek bir kez tekrarlanır. Sunucunun reddettiği yenileme tokenı temizlenir; sonraki açılışta yeniden giriş gerekir. İlk giriş internet bağlantısı ister; kayıtlı hesap çevrimdışıyken uygulamayı açabilir.

## Ölçüm

Emülatörde DEMO modu temsili RSRP, SINR, RSRQ ve 5,0–80,0 Mbps değerleri üretir. DEMO kayıtları `kaynak=simulasyon` olarak gönderilir; gerçek ölçüm değildir.

DEMO kapalıyken hücresel sinyal cihazdan okunur; eksik değerler uydurulmaz. “Ölç + hız testi” yaklaşık 3,5 MB aktarım ile indirme/yükleme hızını ve HTTP gecikmesini ölçer. Gecikme ICMP ping değildir; sonuçlar test sunucusuna ve ağa bağlıdır. Eksik yükleme/gecikme ölçümüyle canlı derse yeterli olduğu söylenmez. Hücre izni olmadan da hız testi yapılabilir. GPS/koordinat API'si çağrılmaz; `enlem` ve `boylam` daima `0.0` gönderilir. Android hücre bilgisi için konum izni isteyebilir; arka plan konum izni eklenmemiştir.

Ölçümler girişte alınan `ogrenci_id` ile WorkManager kuyruğuna eklenir; internet yoksa bağlantı beklenir. Otomatik mod görünür ekranda her 10 saniyede sinyal ölçer; büyük hız testi yapmaz. Her kaydı gönderirken küçük bir HTTP trafiği oluşur.

WorkManager saatte bir akşam penceresini kontrol eder ve 19:00–23:00 arasında öğrenci başına günde bir kez hız testi planlar. Android pil/ağ koşulları nedeniyle kesin saat garanti edilmez. Kota seçilmemiş veya en fazla 2 GB ise arka planda mobil ağdan büyük aktarım yapılmaz; sinyal ve HTTP gecikmesi denenir. “Ödev saati ölçümünü şimdi dene” saat kontrolünü atlar. Kuyruktaki işlemler başka öğrencinin oturumuyla gönderilmez; ilgili öğrencinin yeniden giriş yapmasını bekler.

## Ödev ve sınıf bilgisi

Uygulama `odevler` tablosundan `id`, `baslik`, `video_url`, `sube`, `odev_turu`, `son_tarih` ve `boyut_mb` alanlarını okur. Seçilen şube ve `Tüm şubeler` hedefli ödevleri gösterir. Şube seçimi öğrenci kimliğine göre cihazda saklanır. `ogrenci_sinif.sinif` okul sınıfı değil, bağlantı düzeyidir (1–4); arayüz bunu bağlantı durumu olarak gösterir.

Son ödev listesi cihazda saklanır. Aynı Supabase projesinin public `odevler` Storage alanındaki videolar Android'in kotasız saydığı ağda otomatik indirilir. Her Wi-Fi ağı ücretsiz değildir; indirme koşulu `UNMETERED` ağdır. İndirilen video VideoView ile internetsiz oynatılır. İndirme ve ilk görüntü gösterildiğinde açılma bildirimleri `indirildi` ve `acildi` alanlarına yazılmak üzere kuyruğa alınır. Google arama sayfaları video olarak indirilmez; dış bağlantılar tarayıcıda açılır.

“Tamamladım, öğretmene gönder” artık `odev_durumu.yapildi=true` ve `yapildi_zaman` yazar; video açılmasıyla karıştırılmaz. Çevrimdışı bildirimler kalıcı WorkManager kuyruğunda bekler. Bu öğrenci bildirimi olup öğretmenin ayrı onay kararını göstermez.

Aylık kota seçimi cihazda öğrenciye göre saklanır ve `ogrenci_kota` tablosuna bağlantı gelince gönderilir. Web paneli bu tabloyu okumalıdır; web arayüzünün kodu bu depoda değildir.

## Yeni sürümün sunucu gereksinimi

Uygulamayı kullanmadan önce [SQL ve hız testi fonksiyonu kurulumunu](../database/mobile/README.md) tamamlayın. `database/sql/iyilestirmeler.sql` yeni alanları ve RLS politikalarını ekler. `database/mobile/functions/hiz-testi/index.ts` yükleme testinin sunucu tarafıdır. Dosyaların GitHub'a eklenmesi Supabase'e kurulum yapmaz. Önceki demo SQL dosyaları güncel kurulum sırası değildir.

Supabase RLS politikaları, JWT'deki `app_metadata.ogrenci_id` alanıyla bu kayıtları eşleştirmelidir.
