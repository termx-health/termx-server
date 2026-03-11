-- Drop function if parameter name changed (for migration from Maven to vendored version)
DROP FUNCTION IF EXISTS core.jsonpath(jsonb[], text[]);

CREATE OR REPLACE FUNCTION core.jsonpath(_json jsonb[], _path text[]) RETURNS jsonb[] AS $$
DECLARE 
  result jsonb[];
  element jsonb;
BEGIN 
  result := array[]::jsonb[];
  FOREACH element IN ARRAY _json LOOP
    result := array_cat(result, core.jsonpath(element, _path));
  END LOOP;
  return result;
END;
$$ LANGUAGE plpgsql IMMUTABLE;
