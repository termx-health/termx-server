CREATE OR REPLACE FUNCTION core.jsonb_trunc(json jsonb)
RETURNS JSONB AS $$
DECLARE
  result jsonb;
begin
	result = jsonb_strip_nulls(json);
     IF result = '{}'::jsonb then
       result = null;
     end if;
     IF result = '[]'::jsonb then
       result = null;
     end if;
    return result;
END;
$$ LANGUAGE plpgsql;
