-- Kurulumdaki demo öğrencileri (A, B, C) kaldırır.
-- Ölçüm satırları SİLİNMEZ: simülasyon öğrencilerinin profil havuzu bunlar.
-- Supabase > SQL Editor > yapıştır > Run.
delete from odev_durumu   where ogrenci_id in ('A', 'B', 'C');
delete from ogrenci_sinif where ogrenci_id in ('A', 'B', 'C');
delete from ogrenciler    where id         in ('A', 'B', 'C');

select sube, count(*) as ogrenci from ogrenciler group by sube order by sube;
