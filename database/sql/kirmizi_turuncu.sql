-- Kırmızı (bağlantı yok) simülasyon öğrencilerinin yarısını turuncu (sadece mesaj) yapar.
-- Yöntem: seçilen öğrencilerin simülasyon ölçümleri silinir, yerine turuncu profilin
-- (veritabanındaki %20 hızı 0,1–1 Mbps olan kaynak) ölçümlerinden rastgele kopyalar konur.
-- Supabase > SQL Editor > yapıştır > Run.
with p20 as (
  select ogrenci_id, percentile_cont(0.2) within group (order by hiz_mbps) as p
  from olcumler where hiz_mbps is not null group by ogrenci_id
),
kirmizi as (
  select g.id, g.ev_enlem, g.ev_boylam,
         row_number() over (order by random()) as sira, count(*) over () as adet
  from ogrenciler g join p20 on p20.ogrenci_id = g.id
  where g.kaynak = 'simulasyon' and p20.p < 0.1
),
secilen as (select * from kirmizi where sira <= ceil(adet / 2.0)),
profil as (
  select ogrenci_id from p20
  where p >= 0.1 and p < 1
    and ogrenci_id not in (select id from ogrenciler where kaynak = 'simulasyon')
  limit 1
),
silinen as (
  delete from olcumler o using secilen s where o.ogrenci_id = s.id returning o.id
)
insert into olcumler (ogrenci_id, zaman, enlem, boylam, rsrp, sinr, rsrq, hiz_mbps, kaynak)
select s.id,
       now() - random() * interval '3 hours',
       s.ev_enlem  + (random() - 0.5) * 0.0004,
       s.ev_boylam + (random() - 0.5) * 0.0005,
       o.rsrp, o.sinr, o.rsrq, o.hiz_mbps, 'simulasyon'
from secilen s
cross join profil pr
cross join lateral (
  (select * from olcumler o where o.ogrenci_id = pr.ogrenci_id and o.hiz_mbps is not null
     and s.id is not null order by random() limit 4)
  union all
  (select * from olcumler o where o.ogrenci_id = pr.ogrenci_id and o.hiz_mbps is null
     and s.id is not null order by random() limit 6)
) o;

-- Kontrol: sınıf dağılımı (1 yeşil, 2 sarı, 3 turuncu, 4 kırmızı)
with p20 as (
  select ogrenci_id, percentile_cont(0.2) within group (order by hiz_mbps) as p
  from olcumler where hiz_mbps is not null group by ogrenci_id
)
select case when p >= 2 then '1 yeşil' when p >= 1 then '2 sarı' when p >= 0.1 then '3 turuncu' else '4 kırmızı' end as sinif,
       count(*) as ogrenci
from ogrenciler g join p20 on p20.ogrenci_id = g.id
where g.kaynak = 'simulasyon'
group by 1 order by 1;
