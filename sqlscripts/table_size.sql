-- Чтобы узнать размер таблицы в PostgreSQL, можно воспользоваться встроенными функциями `pg_total_relation_size` (общий размер с индексами и TOAST) или `pg_relation_size` (только данные).
-- Вот несколько способов сделать это:
-- ### 1. Размер конкретной таблицы (в понятном формате)
-- Используйте `pg_size_pretty` для перевода байтов в КБ, МБ или ГБ.

-- Общий размер таблицы (данные + индексы + toast)
SELECT pg_size_pretty(pg_total_relation_size('имя_таблицы'));

-- Только размер данных в таблице
SELECT pg_size_pretty(pg_relation_size('имя_таблицы'));

-- Например, для вашей таблицы candles:
SELECT pg_size_pretty(pg_total_relation_size('candles'));

-- 2. Список самых тяжелых таблиц в базе
-- Если вы хотите увидеть размеры всех таблиц сразу, чтобы понять, кто занимает больше всего места:
SELECT relname                                                                 AS "Table",
       pg_size_pretty(pg_total_relation_size(relid))                           AS "Size",
       pg_size_pretty(pg_total_relation_size(relid) - pg_relation_size(relid)) AS "External Size"
FROM pg_catalog.pg_statio_user_tables
ORDER BY pg_total_relation_size(relid) DESC;

-- 3. Детальная информация (Данные vs Индексы)
-- Этот запрос покажет отдельно вес данных и вес индексов:
SELECT table_name,
       pg_size_pretty(table_size)   AS table_size,
       pg_size_pretty(indexes_size) AS indexes_size,
       pg_size_pretty(total_size)   AS total_size
FROM (SELECT table_name,
             pg_table_size(table_name)          AS table_size,
             pg_indexes_size(table_name)        AS indexes_size,
             pg_total_relation_size(table_name) AS total_size
      FROM (SELECT ('"' || table_schema || '"."' || table_name || '"') AS table_name
            FROM information_schema.tables) AS all_tables
      ORDER BY total_size DESC) AS pretty_sizes;