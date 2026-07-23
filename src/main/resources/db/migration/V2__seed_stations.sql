-- Placeholder seed data only. Codes below are NOT verified against the real
-- eticket.railway.uz station directory - they exist so the bot has
-- something to search against out of the box. Replace/refresh this table
-- from GET /api/v1/stations (see EticketRailwayClient.searchStations) before
-- relying on it in production.
INSERT INTO stations (code, name, name_normalized) VALUES
    ('TASHKENT', 'Toshkent', 'toshkent'),
    ('SAMARKAND', 'Samarqand', 'samarqand'),
    ('BUKHARA', 'Buxoro', 'buxoro'),
    ('URGANCH', 'Urganch', 'urganch'),
    ('NUKUS', 'Nukus', 'nukus'),
    ('ANDIJAN', 'Andijon', 'andijon'),
    ('FERGANA', 'Farg''ona', 'fargona'),
    ('QARSHI', 'Qarshi', 'qarshi'),
    ('NAVOIY', 'Navoiy', 'navoiy'),
    ('KOKAND', 'Qo''qon', 'qoqon'),
    ('KHIVA', 'Xiva', 'xiva'),
    ('TERMIZ', 'Termiz', 'termiz');
