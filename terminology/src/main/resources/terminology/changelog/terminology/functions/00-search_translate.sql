drop function if exists terminology.search_translate;

CREATE OR REPLACE FUNCTION terminology.search_translate(text) RETURNS text AS $$
  select translate(lower($1),
                     'äąęėõöüųūįšžč',
                     'aaeeoouuuiszc'
                   );
$$ LANGUAGE sql IMMUTABLE;
