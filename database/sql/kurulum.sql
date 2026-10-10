-- Kapasite Haritası · Supabase kurulumu (W1 + W2)
-- Supabase > SQL Editor > New query > hepsini yapıştır > Run
-- Tekrar çalıştırılabilir; mevcut veriyi silmez.

-- 1) TABLOLAR (isimler plandaki gibi, DEĞİŞTİRME)
create table if not exists ogrenciler (
  id         text primary key,          -- 'A', 'B', 'C' (mobil uygulama bunu gönderir)
  ad         text not null,
  ev_enlem   double precision,
  ev_boylam  double precision
);

create table if not exists olcumler (
  id          bigint generated always as identity primary key,
  ogrenci_id  text,
  zaman       timestamptz not null default now(),
  enlem       double precision,
  boylam      double precision,
  rsrp        double precision,           -- dBm
  sinr        double precision,           -- dB  (değer yoksa NULL gönderilmeli, 2147483647 DEĞİL)
  rsrq        double precision,           -- dB
  hiz_mbps    double precision            -- sadece hız testi yapılan satırlarda dolu
);
create index if not exists olcumler_zaman_idx on olcumler (zaman desc);

create table if not exists odevler (
  id             bigint generated always as identity primary key,
  baslik         text not null,
  video_url      text,
  gereken_sinif  smallint not null check (gereken_sinif between 1 and 3)  -- 1 canlı, 2 video, 3 mesaj
);

create table if not exists odev_durumu (
  ogrenci_id  text   not null,
  odev_id     bigint not null references odevler(id) on delete cascade,
  indirildi   boolean not null default false,
  acildi      boolean not null default false,
  cevap       text,
  primary key (ogrenci_id, odev_id)       -- mobil taraf upsert yapabilsin diye
);

-- 2) DEMO İÇİN GÜVENLİK KAPALI ("gerçek üründe yetkilendirme açılır")
alter table ogrenciler  disable row level security;
alter table olcumler    disable row level security;
alter table odevler     disable row level security;
alter table odev_durumu disable row level security;

grant usage on schema public to anon, authenticated;
grant select, insert, update, delete on all tables    in schema public to anon, authenticated;
grant usage, select                  on all sequences in schema public to anon, authenticated;

-- 3) STORAGE: herkese açık 'odevler' klasörü (video + 3 MB hız testi dosyası)
insert into storage.buckets (id, name, public)
values ('odevler', 'odevler', true)
on conflict (id) do update set public = true;

drop policy if exists "odevler okuma"     on storage.objects;
drop policy if exists "odevler yukleme"   on storage.objects;
drop policy if exists "odevler guncelleme" on storage.objects;
create policy "odevler okuma"      on storage.objects for select using      (bucket_id = 'odevler');
create policy "odevler yukleme"    on storage.objects for insert with check (bucket_id = 'odevler');
create policy "odevler guncelleme" on storage.objects for update using      (bucket_id = 'odevler');

-- 4) W2: ÖĞRENCİLER (koordinatlar TAHMİNİ, sunum sorumlusu verince güncelle)
insert into ogrenciler (id, ad, ev_enlem, ev_boylam) values
  ('A', 'Öğrenci A', 40.9950, 39.7750),
  ('B', 'Öğrenci B', 40.9960, 39.7770),
  ('C', 'Öğrenci C', 40.9940, 39.7730)
on conflict (id) do nothing;

-- Gerçek koordinat gelince şöyle güncelle:
-- update ogrenciler set ev_enlem = 40.99xx, ev_boylam = 39.77xx where id = 'B';

-- 5) TEST SATIRI (tabloda görünüyorsa kurulum tamam; sonra silebilirsin)
insert into olcumler (ogrenci_id, enlem, boylam, rsrp, sinr, rsrq, hiz_mbps)
values ('A', 40.9950, 39.7750, -95, 12, -10, 5.2);
-- delete from olcumler where id = 1;
