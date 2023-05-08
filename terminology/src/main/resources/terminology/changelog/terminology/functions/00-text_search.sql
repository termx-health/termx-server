CREATE OR REPLACE FUNCTION terminology.text_search(VARIADIC text[]) RETURNS text AS $$
  select '`' || string_agg(terminology.search_translate(val), '`') || '`'
  from unnest($1) as val;
$$ LANGUAGE sql IMMUTABLE;

