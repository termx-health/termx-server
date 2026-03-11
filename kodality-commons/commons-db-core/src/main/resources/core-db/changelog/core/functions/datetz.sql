CREATE OR REPLACE FUNCTION core.tzts(_ts timestamp without time zone, _from_tz text = 'Europe/Tallinn') 
  RETURNS timestamptz AS
$func$
SELECT _ts AT TIME ZONE _from_tz
$func$ LANGUAGE sql IMMUTABLE;

create or replace FUNCTION core.tzdate(_ts timestamp with time zone, _to_tz text = 'Europe/Tallinn') 
  RETURNS date AS
$func$
SELECT (_ts AT TIME ZONE _to_tz)::date 
$func$ LANGUAGE sql IMMUTABLE;

CREATE OR REPLACE FUNCTION core.tzdate(_ts timestamp without time zone, _from_tz text = 'Europe/Tallinn', _to_tz text = 'Europe/Tallinn') 
  RETURNS date AS
$func$
SELECT core.tzdate(core.tzts(_ts, _from_tz), _to_tz); 
$func$ LANGUAGE sql IMMUTABLE;

