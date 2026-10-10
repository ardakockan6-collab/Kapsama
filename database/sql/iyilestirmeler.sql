-- ============================================================
-- iyilestirmeler.sql — KAPSAMA (irjizszyfswkuonawtqi)
-- Uygulamanın yeni sürümünden ÖNCE çalıştırılır.
-- Şu an telefondaki sürüm de bu değişiklikten sonra çalışmaya devam eder.
-- ============================================================

-- ===== 1) GÜVENLİK: anahtar tek başına hiçbir şey okuyamaz / yazamaz =====
drop policy if exists durum_telefon_oku      on public.odev_durumu;
drop policy if exists durum_telefon_ekle     on public.odev_durumu;
drop policy if exists durum_telefon_guncelle on public.odev_durumu;
drop policy if exists sinif_telefon_oku      on public.ogrenci_sinif;
drop policy if exists sinif_telefon_ekle     on public.ogrenci_sinif;
drop policy if exists sinif_telefon_guncelle on public.ogrenci_sinif;
drop policy if exists olcum_telefon_yazar    on public.olcumler;
drop policy if exists odev_oku               on public.odevler;

-- öğrenci sadece kendi koduyla ölçüm yazabilir
drop policy if exists olcum_ogrenci_yazar on public.olcumler;
create policy olcum_ogrenci_yazar on public.olcumler for insert to authenticated
  with check (ogrenci_id = public.benim_ogrenci_id());

-- ödevleri sadece giriş yapan okur
create policy odev_oku on public.odevler for select to authenticated using (true);

revoke all on public.olcumler, public.odevler, public.odev_durumu,
              public.ogrenci_sinif, public.ogrenciler, public.ogrenci_listesi from anon;

-- ===== 2) ÖLÇÜM: yükleme hızı, gecikme, ağ türü, ölçüm türü =====
alter table public.olcumler
  add column if not exists yukleme_mbps double precision,
  add column if not exists ping_ms      double precision,
  add column if not exists ag           text check (ag in ('wifi', 'mobil')),
  add column if not exists olcum_turu   text check (olcum_turu in ('elle', 'odev_saati', 'odev_saati_deneme'));

-- ===== 3) ÖDEV: son teslim ve dosya boyutu =====
alter table public.odevler
  add column if not exists son_tarih timestamptz,
  add column if not exists boyut_mb  numeric;

-- Storage'daki mevcut videoların boyutunu doldur
update public.odevler o
set boyut_mb = round((s.metadata->>'size')::numeric / 1048576, 1)
from storage.objects s
where s.bucket_id = 'odevler'
  and o.video_url like '%/storage/v1/object/public/odevler/%'
  and s.name = split_part(o.video_url, '/storage/v1/object/public/odevler/', 2)
  and o.boyut_mb is null;

-- ===== 4) KOTA: öğrencinin bildirdiği aylık mobil veri kotası =====
create table if not exists public.ogrenci_kota (
  ogrenci_id text primary key,
  kota_gb    numeric check (kota_gb > 0),
  sinirsiz   boolean not null default false,
  guncelleme timestamptz not null default now()
);
alter table public.ogrenci_kota enable row level security;

drop policy if exists kota_ogretmen          on public.ogrenci_kota;
drop policy if exists kota_ogrenci_oku       on public.ogrenci_kota;
drop policy if exists kota_ogrenci_ekle      on public.ogrenci_kota;
drop policy if exists kota_ogrenci_guncelle  on public.ogrenci_kota;
create policy kota_ogretmen on public.ogrenci_kota for all to authenticated
  using (public.is_ogretmen()) with check (public.is_ogretmen());
create policy kota_ogrenci_oku on public.ogrenci_kota for select to authenticated
  using (ogrenci_id = public.benim_ogrenci_id());
create policy kota_ogrenci_ekle on public.ogrenci_kota for insert to authenticated
  with check (ogrenci_id = public.benim_ogrenci_id());
create policy kota_ogrenci_guncelle on public.ogrenci_kota for update to authenticated
  using (ogrenci_id = public.benim_ogrenci_id()) with check (ogrenci_id = public.benim_ogrenci_id());

revoke all on public.ogrenci_kota from anon;
grant select, insert, update, delete on public.ogrenci_kota to authenticated;  -- silmeyi RLS sadece öğretmene açar

-- ===== KONTROL (sadece okur) =====
-- select
--   (select count(*) from pg_policies where schemaname = 'public' and roles @> array['anon']::name[]) as anon_kurali,
--   (select string_agg(column_name, ', ') from information_schema.columns
--     where table_schema = 'public' and table_name = 'olcumler'
--       and column_name in ('yukleme_mbps', 'ping_ms', 'ag', 'olcum_turu')) as olcum_yeni_sutunlar,
--   (select string_agg(column_name, ', ') from information_schema.columns
--     where table_schema = 'public' and table_name = 'odevler'
--       and column_name in ('son_tarih', 'boyut_mb')) as odev_yeni_sutunlar,
--   (to_regclass('public.ogrenci_kota') is not null) as kota_tablosu,
--   (select (metadata->>'size')::bigint from storage.objects
--     where bucket_id = 'odevler' and name = 'hiztesti.bin') as hiztesti_bayt,
--   (select string_agg(baslik || ' = ' || coalesce(boyut_mb::text, '?') || ' MB', ', ') from public.odevler) as odev_boyutlari;
