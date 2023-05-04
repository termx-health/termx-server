drop function if exists terminology.jsonb_search;

CREATE OR REPLACE FUNCTION terminology.jsonb_search(jsonb) RETURNS text AS $$
  select '`' || string_agg(terminology.search_translate(values.value), '`') || '`'
  from jsonb_each_text($1) as values;
$$ LANGUAGE sql IMMUTABLE;

