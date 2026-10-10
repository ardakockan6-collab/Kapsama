-- Kapasite Haritası · Güvenlik (RLS + yetkiler)
-- ÖNCE: Authentication > Users > Add user > Create new user
--        e-posta: ogretmen@kapasite.demo  · güçlü bir şifre · "Auto Confirm User" işaretli
-- SONRA: bu dosyanın tamamını SQL Editor'da çalıştır. Tekrar çalıştırılabilir.
--
-- Sonuç:
--   anon (APK'daki anahtar): ölçüm YAZAR ama OKUYAMAZ; ev koordinatlarını hiç göremez;
--                            ödev listesini okur, kendi ödev durumunu ve sınıfını yazar.
--   öğretmen (panel girişi): her şeyi okur/yazar.

-- 0) Öğretmen kontrolü (giriş yapan kullanıcının e-postası)
create or replace function public.is_ogretmen() returns boolean
language sql stable as $$
  select coalesce(auth.jwt() ->> 'email', '') = 'ogretmen@kapasite.demo'
$$;

-- ogrenci_sinif tablosu henüz kurulmadıysa
create table if not exists ogrenci_sinif (
  ogrenci_id  text primary key,
  sinif       smallint not null check (sinif between 1 and 4),
  guncelleme  timestamptz not null default now()
);

-- 1) RLS AÇ
alter table ogrenciler    enable row level security;
alter table olcumler      enable row level security;
alter table odevler       enable row level security;
alter table odev_durumu   enable row level security;
alter table ogrenci_sinif enable row level security;

-- 2) TABLO YETKİLERİ (RLS'nin altındaki ikinci kilit)
revoke all on ogrenciler, olcumler, odevler, odev_durumu, ogrenci_sinif from anon;
grant insert                 on olcumler                   to anon;   -- yaz, OKUMA YOK
grant select                 on odevler                    to anon;
grant select, insert, update on odev_durumu, ogrenci_sinif to anon;
grant select, insert, update, delete
  on ogrenciler, olcumler, odevler, odev_durumu, ogrenci_sinif to authenticated;

-- 3) POLİTİKALAR
-- olcumler: telefon yazar, sadece öğretmen okur
drop policy if exists olcum_telefon_yazar  on olcumler;
drop policy if exists olcum_ogretmen       on olcumler;
create policy olcum_telefon_yazar on olcumler for insert to anon, authenticated with check (true);
create policy olcum_ogretmen      on olcumler for all    to authenticated
  using (public.is_ogretmen()) with check (public.is_ogretmen());

-- ogrenciler (ev koordinatları): sadece öğretmen
drop policy if exists ogr_ogretmen on ogrenciler;
create policy ogr_ogretmen on ogrenciler for all to authenticated
  using (public.is_ogretmen()) with check (public.is_ogretmen());

-- odevler: herkes okur, öğretmen yazar
drop policy if exists odev_oku      on odevler;
drop policy if exists odev_ogretmen on odevler;
create policy odev_oku      on odevler for select to anon, authenticated using (true);
create policy odev_ogretmen on odevler for all    to authenticated
  using (public.is_ogretmen()) with check (public.is_ogretmen());

-- odev_durumu ve ogrenci_sinif: telefon okur/yazar (upsert için), öğretmen hepsi
drop policy if exists durum_telefon  on odev_durumu;
drop policy if exists durum_ogretmen on odev_durumu;
create policy durum_telefon  on odev_durumu for all to anon using (true) with check (true);
create policy durum_ogretmen on odev_durumu for all to authenticated
  using (public.is_ogretmen()) with check (public.is_ogretmen());

drop policy if exists sinif_telefon  on ogrenci_sinif;
drop policy if exists sinif_ogretmen on ogrenci_sinif;
create policy sinif_telefon  on ogrenci_sinif for all to anon using (true) with check (true);
create policy sinif_ogretmen on ogrenci_sinif for all to authenticated
  using (public.is_ogretmen()) with check (public.is_ogretmen());

-- 4) STORAGE: video ve hız dosyasını herkes indirir, sadece öğretmen yükler
drop policy if exists "odevler yukleme"    on storage.objects;
drop policy if exists "odevler guncelleme" on storage.objects;
create policy "odevler yukleme" on storage.objects for insert to authenticated
  with check (bucket_id = 'odevler' and public.is_ogretmen());
create policy "odevler guncelleme" on storage.objects for update to authenticated
  using (bucket_id = 'odevler' and public.is_ogretmen());

-- 5) KANIT (jüriye gösterilecek): tarayıcıya yaz
--   https://PROJE.supabase.co/rest/v1/olcumler?select=*&apikey=ANON_KEY
--   → "permission denied for table olcumler"
