-- Ödevler tablosunu okunur yapar. Supabase > SQL Editor > yapıştır > Run. Tekrar çalıştırılabilir.

-- 1) gereken_sinif'in yanına yazıyla gösteren sütun (otomatik dolar, elle girilmez)
--    gereken_sinif sayı olarak KALIR: panel ve mobil uygulama bununla hesap yapıyor.
alter table odevler add column if not exists sube text;
alter table odevler add column if not exists gereken_ad text
  generated always as (
    case gereken_sinif when 1 then 'Canlı ders' when 2 then 'Video' when 3 then 'Sadece mesaj' end
  ) stored;

-- 2) Şube boşsa "Tüm şubeler" yaz; yeni ödevlerde de varsayılan bu olsun
update odevler set sube = 'Tüm şubeler' where sube is null or sube = '';
alter table odevler alter column sube set default 'Tüm şubeler';

select id, baslik, gereken_sinif, gereken_ad, sube from odevler order by id;
