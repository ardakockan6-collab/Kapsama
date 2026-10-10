-- Simülasyon öğrencilerine rastgele (uydurma) Türkçe isimler verir.
-- Supabase > SQL Editor > yapıştır > Run. Her çalıştırmada isimler yeniden karılır.
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

select id, ad, sube from ogrenciler order by sube, id;
