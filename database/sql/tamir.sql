-- TAMİR: ölçüm havuzunu, 30 öğrencinin ölçümlerini ve isimlerini tek seferde yeniden kurar.
-- Supabase > SQL Editor > hepsini yapıştır > Run.

-- 1) Ölçümleri işaretlemek için sütun (mobil uygulamayı etkilemez, varsayılan 'olcum')
alter table olcumler add column if not exists kaynak text not null default 'olcum';

-- 2) Ev konumları (simülasyon için kampüs noktaları)
update ogrenciler set ad = 'Öğrenci A', ev_enlem = 40.99294, ev_boylam = 39.77546 where id = 'A';
update ogrenciler set ad = 'Öğrenci B', ev_enlem = 40.99791, ev_boylam = 39.77193 where id = 'B';
update ogrenciler set ad = 'Öğrenci C', ev_enlem = 40.99548, ev_boylam = 39.76882 where id = 'C';

-- 3) Eski simülasyon varsa temizle, yenisini ekle
delete from olcumler where kaynak = 'simulasyon';
insert into olcumler (ogrenci_id, zaman, enlem, boylam, rsrp, sinr, rsrq, hiz_mbps, kaynak) values
('A','2026-10-09 18:30:00+03',40.992926,39.775438,-90,15.3,-12,null,'simulasyon'),
('A','2026-10-09 18:30:40+03',40.992883,39.775309,-91,16.5,-9,null,'simulasyon'),
('A','2026-10-09 18:31:20+03',40.992959,39.775584,-92,15.2,-11,38.878,'simulasyon'),
('A','2026-10-09 18:32:00+03',40.992959,39.775595,-86,14.0,-12,null,'simulasyon'),
('A','2026-10-09 18:32:40+03',40.992915,39.775361,-91,19.7,-9,null,'simulasyon'),
('A','2026-10-09 18:33:20+03',40.992975,39.775491,-85,17.6,-11,48.082,'simulasyon'),
('A','2026-10-09 18:34:00+03',40.992811,39.775420,-89,17.3,-11,null,'simulasyon'),
('A','2026-10-09 18:34:40+03',40.992848,39.775348,-87,18.2,-9,null,'simulasyon'),
('A','2026-10-09 18:35:20+03',40.992950,39.775555,-91,15.4,-10,35.241,'simulasyon'),
('A','2026-10-09 18:36:00+03',40.992886,39.775379,-89,15.3,-10,null,'simulasyon'),
('A','2026-10-09 18:36:40+03',40.993040,39.775404,-87,15.0,-12,null,'simulasyon'),
('A','2026-10-09 18:37:20+03',40.992983,39.775554,-84,17.8,-10,47.814,'simulasyon'),
('A','2026-10-09 18:38:00+03',40.993005,39.775329,-90,14.2,-11,null,'simulasyon'),
('A','2026-10-09 18:38:40+03',40.992933,39.775541,-84,19.3,-9,null,'simulasyon'),
('B','2026-10-09 18:42:00+03',40.997863,39.771837,-89,-5.6,-9,null,'simulasyon'),
('B','2026-10-09 18:42:40+03',40.997912,39.772064,-94,-5.2,-11,null,'simulasyon'),
('B','2026-10-09 18:43:20+03',40.997859,39.771979,-88,-5.5,-9,0.593,'simulasyon'),
('B','2026-10-09 18:44:00+03',40.997992,39.772020,-90,-5.7,-9,null,'simulasyon'),
('B','2026-10-09 18:44:40+03',40.997811,39.772051,-92,-4.1,-11,null,'simulasyon'),
('B','2026-10-09 18:45:20+03',40.998024,39.771941,-91,-5.4,-9,0.866,'simulasyon'),
('B','2026-10-09 18:46:00+03',40.997978,39.772006,-92,-4.1,-11,null,'simulasyon'),
('B','2026-10-09 18:46:40+03',40.997902,39.772056,-95,-4.3,-11,null,'simulasyon'),
('B','2026-10-09 18:47:20+03',40.997878,39.772066,-91,-6.5,-11,0.655,'simulasyon'),
('B','2026-10-09 18:48:00+03',40.997928,39.771802,-96,-6.0,-9,null,'simulasyon'),
('B','2026-10-09 18:48:40+03',40.998024,39.771858,-89,-6.1,-9,null,'simulasyon'),
('B','2026-10-09 18:49:20+03',40.998004,39.771809,-95,-5.5,-9,0.9,'simulasyon'),
('B','2026-10-09 18:50:00+03',40.997915,39.771866,-92,-5.4,-9,null,'simulasyon'),
('B','2026-10-09 18:50:40+03',40.997971,39.771847,-94,-5.4,-11,null,'simulasyon'),
('C','2026-10-09 18:54:00+03',40.995503,39.768915,-117,-11.1,-10,null,'simulasyon'),
('C','2026-10-09 18:54:40+03',40.995433,39.768813,-122,-12.0,-10,null,'simulasyon'),
('C','2026-10-09 18:55:20+03',40.995488,39.768903,-124,-11.1,-9,0.061,'simulasyon'),
('C','2026-10-09 18:56:00+03',40.995503,39.768783,-122,-11.0,-9,null,'simulasyon'),
('C','2026-10-09 18:56:40+03',40.995568,39.768948,-120,-11.6,-11,null,'simulasyon'),
('C','2026-10-09 18:57:20+03',40.995501,39.768747,-123,-11.7,-10,0.037,'simulasyon'),
('C','2026-10-09 18:58:00+03',40.995466,39.768675,-117,-12.7,-10,null,'simulasyon'),
('C','2026-10-09 18:58:40+03',40.995413,39.768960,-122,-12.4,-10,null,'simulasyon'),
('C','2026-10-09 18:59:20+03',40.995417,39.768864,-122,-10.2,-9,0.072,'simulasyon'),
('C','2026-10-09 19:00:00+03',40.995451,39.768808,-117,-10.5,-11,null,'simulasyon'),
('C','2026-10-09 19:00:40+03',40.995591,39.768747,-123,-11.5,-9,null,'simulasyon'),
('C','2026-10-09 19:01:20+03',40.995453,39.768845,-121,-10.0,-9,0.071,'simulasyon'),
('C','2026-10-09 19:02:00+03',40.995396,39.768852,-117,-10.0,-10,null,'simulasyon'),
('C','2026-10-09 19:02:40+03',40.995470,39.768751,-122,-10.1,-10,null,'simulasyon');


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
with havuz as (           -- hız testi olan kaynak profiller (A, B, C ölçümleri; öğrenci kaydı silinmiş olsa da)
  select array_agg(distinct ogrenci_id) as dizi
  from olcumler
  where hiz_mbps is not null
    and ogrenci_id not in (select id from ogrenciler where kaynak = 'simulasyon')
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

with adlar as (
  select ad, row_number() over (order by random()) as sira
  from unnest(array[
    'Elif','Zeynep','Ayşe','Defne','Ecrin','Azra','Nehir','Asel','Mira','Duru',
    'Yusuf','Eymen','Ömer','Mustafa','Kerem','Emir','Aras','Çınar','Alp','Kuzey',
    'Ela','Selin','Buse','İrem','Melis','Arda','Berk','Efe','Mert','Can'
  ]) as ad
),
soyadlar as (
  select soyad, row_number() over (order by random()) as sira
  from unnest(array[
    'Yılmaz','Kaya','Demir','Şahin','Çelik','Yıldız','Yıldırım','Öztürk','Aydın','Özdemir',
    'Arslan','Doğan','Kılıç','Aslan','Çetin','Kara','Koç','Kurt','Özkan','Şimşek',
    'Polat','Korkmaz','Erdoğan','Güneş','Aksoy','Bulut','Karadeniz','Uzun','Tekin','Akın'
  ]) as soyad
),
ogr as (
  select id, row_number() over (order by random()) as sira
  from ogrenciler where kaynak = 'simulasyon'
)
update ogrenciler g
set ad = a.ad || ' ' || s.soyad
from ogr o
join adlar a    on a.sira = o.sira
join soyadlar s on s.sira = o.sira
where g.id = o.id;

-- Kontrol: şube başına öğrenci, ölçüm, isimsiz öğrenci
select g.sube, count(distinct g.id) as ogrenci, count(o.id) as olcum,
       count(distinct g.id) filter (where g.ad like 'Öğrenci %') as isimsiz
from ogrenciler g left join olcumler o on o.ogrenci_id = g.id
group by g.sube order by g.sube;
