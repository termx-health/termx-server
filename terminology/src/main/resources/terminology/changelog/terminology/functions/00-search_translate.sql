drop function if exists terminology.search_translate;

CREATE OR REPLACE FUNCTION terminology.search_translate(text) RETURNS text AS $$
  select translate(lower(regexp_replace($1, '[^\w]+',' ','g')),
                     'äąęėõöüųūįšžč',
                     'aaeeoouuuiszc'
                   );
$$ LANGUAGE sql IMMUTABLE;
