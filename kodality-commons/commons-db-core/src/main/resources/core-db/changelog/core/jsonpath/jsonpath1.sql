CREATE OR REPLACE FUNCTION core.jsonpath(_json jsonb, _path text) RETURNS jsonb[] AS $$
BEGIN 
  RETURN core.jsonpath(_json, string_to_array(_path, '.'));
END;
$$ LANGUAGE plpgsql IMMUTABLE;
