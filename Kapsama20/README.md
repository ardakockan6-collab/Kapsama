# Kapsama 2.0

Android Studio ile `Kapsama20` klasörünü açın. Minimum Android sürümü API 28'dir.

## Supabase ayarı

`Kapsama20/local.properties` dosyasına aşağıdaki satırları ekleyin (Android Studio'nun oluşturduğu `sdk.dir` satırını koruyun):

```properties
supabase.url=https://PROJE.supabase.co
supabase.key=SUPABASE_PUBLISHABLE_KEY
```

Bu dosya Git'e eklenmez. Supabase ayarı olmadan uygulama derlenir ancak ölçümler yüklenemez.

## Ölçüm

Emülatörde DEMO modu temsili RSRP, SINR, RSRQ ve 5,0–80,0 Mbps değerleri üretir. DEMO ölçümleri de `olcumler` tablosuna gönderilir. Gerçek cihazda DEMO modu kapatıldığında Android'in erişebildiği hücresel sinyal değerleri kullanılır; hız testi yapılmaz. Konum okunmaz; `enlem` ve `boylam` değerleri `0.0` gönderilir.