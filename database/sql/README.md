# SQL dosyaları — eski demo betikleri

Bu klasör, ekipten alınan dokuz SQL dosyasını özgün içerikleriyle saklar. Otomatik migration değildir; depoya eklenmeleri Supabase üzerinde çalıştırıldıkları anlamına gelmez.

## İçerik

| Dosya | Amaç |
| --- | --- |
| `kurulum.sql` | İlk demo tabloları, Storage bucket/politikaları, A/B/C öğrencileri ve örnek ölçüm. |
| `guvenlik.sql` | Eski e-posta tabanlı öğretmen kontrolü, RLS ve tablo/Storage izinleri. |
| `odev_etiket.sql` | Eski `gereken_sinif` alanından üretilen `gereken_ad` ve şube varsayılanları. |
| `subeler_simulasyon.sql` | 9-A, 9-B ve 9-C için simülasyon öğrencileri ve ölçümleri. |
| `simulasyon_hepsi.sql` | Birleşik ölçüm havuzu, öğrenci simülasyonu, isimler, A/B/C temizliği ve renk dağılımı. |
| `isimler.sql` | Simülasyon öğrencilerine rastgele örnek isimler. |
| `kirmizi_turuncu.sql` | Seçilen simülasyon öğrencilerinin ölçümlerini başka bir profille değiştirir. |
| `tamir.sql` | Ölçüm havuzunu ve simülasyon öğrencilerini yeniden oluşturur. |
| `demo_sil.sql` | A/B/C öğrencilerini, sınıf ve ödev durumlarını siler; ölçümlerini saklar. |

## Güncel uygulamayla farkları

- Güncel mobil uygulama `Kapsama20` klasöründedir; öğrenci kimliğini JWT içindeki `app_metadata.ogrenci_id` alanından alır. Bu SQL dosyaları güncel Auth kurulumunun tamamını içermez.
- `kurulum.sql`, RLS'yi kapatır ve anonim kullanıcılara geniş tablo yetkileri verir. Canlı veritabanında mevcut güvenlik ayarlarını değiştirebilir.
- `guvenlik.sql`, anonim kullanıcılar için ödev durumu ve sınıf kayıtlarına geniş erişim verir. Anonim isteklerde öğrenci kimliğinin sahipliğini doğrulamaz; güncel öğrenciye özel Auth politikalarının yerine kullanılmamalıdır.
- `kurulum.sql` ve `odev_etiket.sql`, eski `gereken_sinif` şemasını kullanır. Güncel mobil uygulama ödev türünü `odev_turu` alanından okur. Bu dosyalar tek başına güncel uygulamanın veritabanı kurulumunu sağlamaz.
- Simülasyon ve bakım dosyalarında `DELETE`/`UPDATE` komutları vardır. Birleşik dosya diğer betiklerle örtüşür; tüm dosyaları sırayla çalıştırmak için bir kurulum sırası tanımlanmamıştır.
- SQL içindeki koordinatlar ve isimler demo/simülasyon verisidir. Mobil uygulamanın koordinat okumaması ve `0.0` göndermesiyle ayrı bir veri akışıdır.

Çalıştırmadan önce hedef veritabanının şeması, RLS politikaları ve etkilenecek kayıtlar incelenmelidir. Dosyalar bu yükleme sırasında veritabanında çalıştırılmadı.

Ek olarak gönderilen eski `UploadWorker.kt`, yalnızca yerel JSONL kaydı yapar ve HTTP gönderimi içermez. Güncel Supabase worker'ının yerini almadığı için bu SQL arşivine dahil edilmedi.
