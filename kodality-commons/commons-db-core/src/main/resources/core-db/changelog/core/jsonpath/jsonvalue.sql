CREATE OR REPLACE FUNCTION core.jsonvalue(_json jsonb, _path text) RETURNS text AS $$
DECLARE
  result jsonb[];
BEGIN 
  result := core.jsonpath(_json, string_to_array(_path, '.'));
  return trim('"' from result[1]::text);
END;
$$ LANGUAGE plpgsql IMMUTABLE;

