-- Station codes are Express-3 codes as used by eticket.railway.uz.
-- VERIFIED from captured browser traffic (2026-07-23):
--   2900000 = Tashkent, 2900790 = Urgench.
-- The remaining codes are commonly cited for this system but UNVERIFIED -
-- confirm them against the site's station handbook (or a HAR capture of the
-- station autocomplete) before relying on them, and extend this table the
-- same way for more stations.
INSERT INTO stations (code, name, name_normalized) VALUES
    ('2900000', 'Toshkent', 'toshkent'),
    ('2900790', 'Urganch', 'urganch'),
    ('2900700', 'Samarqand', 'samarqand'),
    ('2900800', 'Buxoro', 'buxoro'),
    ('2900172', 'Xiva', 'xiva'),
    ('2900970', 'Nukus', 'nukus'),
    ('2900300', 'Andijon', 'andijon');
