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

Emülatörde DEMO modu temsili RSRP, SINR, RSRQ ve 5,0–80,0 Mbps değerleri üretir. DEMO kayıtları da `olcumler` tablosuna gönderilir; bu değerler gerçek ölçüm değildir.

Gerçek cihazda DEMO modu kapatıldığında Android'in erişebildiği hücresel sinyal değerleri kullanılır. Cihazın bildirmediği değerler boş gönderilir. Gerçek hız testi yapılmaz. Konum koordinatları okunmaz; `enlem` ve `boylam` `0.0` gönderilir. Android, hücre bilgisine erişim için konum izni isteyebilir.

Ölçümler girişte alınan `ogrenci_id` ile WorkManager kuyruğuna eklenir. İnternet yoksa bağlantı beklenir. Otomatik mod, uygulama ekranı açıkken her 10 saniyede bir ölçüm başlatır.

## Ödev ve sınıf bilgisi

Uygulama `public.odevler` tablosundan `id`, `baslik`, `video_url`, `sube` ve `odev_turu` alanlarını okur. Seçilen şubeye ve `Tüm şubeler` hedefli ödevlere yer verir. Şube seçimi öğrenci kimliğine göre cihazda saklanır. `ogrenci_sinif` tablosundaki `sinif` alanı aynı `ogrenci_id` ile okunur; kayıt varsa sınıf bilgisi gösterilir. Sınıf tablosunda olmayan bir kayıt uygulama tarafından otomatik oluşturulmaz.

Son alınan ödev listesi cihazda saklanır. `video_url` cihazın uygun uygulamasında açılır. Google arama bağlantıları doğrudan video değildir. Çevrimdışı oynatma için cihazdaki video dosyasını seçme özelliği ayrıdır.

Öğrenci “Tamamladım, öğretmene gönder” düğmesine bastığında `odev_durumu` tablosuna `odev_id`, `ogrenci_id` ve `acildi=true` gönderilir. İnternet yoksa WorkManager bağlantı gelene kadar bekler. Bu sürüm öğretmenin ayrı onay kararını okumaz ve `yapildi` alanını güncellemez; öğretmen onayı için web ve mobil arasında ayrıca bir durum sözleşmesi gerekir.

Supabase RLS politikaları, JWT'deki `app_metadata.ogrenci_id` alanıyla bu kayıtları eşleştirmelidir.
