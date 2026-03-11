-- Drop function if parameter name changed (for migration from Maven to vendored version)
DROP FUNCTION IF EXISTS core.jsonb_trunc(jsonb);

CREATE OR REPLACE FUNCTION core.jsonb_trunc(_json JSONB)
RETURNS JSONB AS $$
DECLARE
  result jsonb;
begin
  result = _json;

  if jsonb_typeof(result) = 'object' then
    select jsonb_object_agg(key, core.jsonb_trunc(value))
    into result
    from jsonb_each(result);
  end if;

  result = jsonb_strip_nulls(result);
  IF result = '{}'::jsonb then
    result = null;
  end if;
  IF result = '[]'::jsonb then
    result = null;
  end if;
  return result;
END;
$$ LANGUAGE plpgsql;
