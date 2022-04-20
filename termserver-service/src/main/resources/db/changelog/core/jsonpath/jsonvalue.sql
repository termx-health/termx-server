CREATE OR REPLACE FUNCTION jsonvalue(json jsonb, path text) RETURNS text AS $$
DECLARE
  result jsonb[];
BEGIN 
  result := jsonpath(json, string_to_array(path, '.'));
  return trim('"' from result[1]::text);
END;
$$ LANGUAGE plpgsql IMMUTABLE;

