-- 3 ŞUBE × 10 ÖĞRENCİ (SİMÜLASYON)
-- Her öğrenciye, veritabanındaki mevcut öğrencilerden (A, B, C) rastgele biri "profil" olarak seçilir;
-- o profilin gerçek/simülasyon ölçüm satırlarından rastgele örnekler kopyalanıp öğrencinin evinin çevresine konur.
-- Hepsi kaynak = 'simulasyon' olarak işaretlidir; panel bunları uyarı bandıyla gösterir.
-- Supabase > SQL Editor > yapıştır > Run. Tekrar çalıştırılabilir (öncekini silip yeniden üretir).

-- 1) Yeni sütunlar (mobil uygulamayı etkilemez)
alter table ogrenciler add column if not exists sube   text;
alter table ogrenciler add column if not exists kaynak text not null default 'gercek';
alter table odevler    add column if not exists sube   text;                       -- boş = tüm şubeler
alter table olcumler   add column if not exists kaynak text not null default 'olcum';
update ogrenciler set sube = 'Demo' where id in ('A', 'B', 'C') and sube is null;

-- 2) Önceki simülasyon öğrencilerini temizle
delete from odev_durumu where ogrenci_id in (select id from ogrenciler where kaynak = 'simulasyon');
delete from olcumler    where ogrenci_id in (select id from ogrenciler where kaynak = 'simulasyon');
delete from ogrenciler  where kaynak = 'simulasyon';

-- 3) 30 öğrenci: 9-A, 9-B, 9-C; evler kampüs çevresinde ~1,5 km içinde rastgele
insert into ogrenciler (id, ad, sube, ev_enlem, ev_boylam, kaynak)
select s.kod || '-' || lpad(n::text, 2, '0'),
       'Öğrenci ' || s.kod || '-' || lpad(n::text, 2, '0'),
       s.sube,
       40.9950 + (random() - 0.5) * 0.024,
       39.7720 + (random() - 0.5) * 0.032,
       'simulasyon'
from (values ('9A', '9-A'), ('9B', '9-B'), ('9C', '9-C')) as s(kod, sube),
     generate_series(1, 10) as n;

-- 4) Ölçümleri mevcut verilerden rastgele çek
with havuz as (           -- hız testi olan kaynak öğrenciler (A, B, C)
  select array_agg(distinct ogrenci_id) as dizi
  from olcumler
  where hiz_mbps is not null
    and ogrenci_id in (select id from ogrenciler where kaynak <> 'simulasyon')
),
yeni as (                 -- her yeni öğrenciye rastgele bir profil
  select g.id, g.ev_enlem, g.ev_boylam,
         h.dizi[1 + floor(random() * cardinality(h.dizi))::int] as profil
  from ogrenciler g, havuz h
  where g.kaynak = 'simulasyon'
)
insert into olcumler (ogrenci_id, zaman, enlem, boylam, rsrp, sinr, rsrq, hiz_mbps, kaynak)
select y.id,
       now() - random() * interval '3 hours',
       y.ev_enlem  + (random() - 0.5) * 0.0004,     -- evin ~20 m çevresi
       y.ev_boylam + (random() - 0.5) * 0.0005,
       o.rsrp, o.sinr, o.rsrq, o.hiz_mbps, 'simulasyon'
from yeni y
cross join lateral (
  (select * from olcumler o where o.ogrenci_id = y.profil and o.hiz_mbps is not null
     and y.id is not null order by random() limit 4)          -- 4 hız testi
  union all
  (select * from olcumler o where o.ogrenci_id = y.profil and o.hiz_mbps is null
     and y.id is not null order by random() limit 6)          -- 6 sinyal ölçümü
) o;

-- 5) Kontrol: şube başına öğrenci ve ölçüm sayısı
select g.sube, count(distinct g.id) as ogrenci, count(o.id) as olcum
from ogrenciler g left join olcumler o on o.ogrenci_id = g.id
group by g.sube order by g.sube;

-- SİLMEK İÇİN:
-- delete from odev_durumu where ogrenci_id in (select id from ogrenciler where kaynak = 'simulasyon');
-- delete from olcumler    where ogrenci_id in (select id from ogrenciler where kaynak = 'simulasyon');
-- delete from ogrenciler  where kaynak = 'simulasyon';
