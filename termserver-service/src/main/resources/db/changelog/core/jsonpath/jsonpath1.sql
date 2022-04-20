CREATE OR REPLACE FUNCTION jsonpath(json jsonb, path text) RETURNS jsonb[] AS $$
BEGIN 
  RETURN jsonpath(json, string_to_array(path, '.'));
END;
$$ LANGUAGE plpgsql IMMUTABLE;
