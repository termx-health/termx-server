CREATE OR REPLACE FUNCTION core.jsonpath(_json jsonb, _path text[]) RETURNS jsonb[] AS $$
BEGIN 
  IF jsonb_typeof(_json) = 'array' THEN
     return core.jsonpath(ARRAY(select jsonb_array_elements(_json)), _path);
  END IF;
  IF array_length(_path, 1) IS NULL THEN
    return array[json#>'{}'];
  END IF;
  IF jsonb_typeof(_json) <> 'object' THEN
     return array[]::jsonb[];
  END IF;
  RETURN core.jsonpath(_json->_path[1], _path[2:array_length(_path, 1)]);
END;
$$ LANGUAGE plpgsql IMMUTABLE;
