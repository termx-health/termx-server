CREATE OR REPLACE FUNCTION core.jsonb_trunc(json jsonb)
RETURNS JSONB AS $$
DECLARE
  result jsonb;
begin
	result = jsonb_strip_nulls(json);
    IF jsonb_typeof(result) = 'array' THEN
     with t as (select core.jsonb_trunc(jsonb_array_elements(result)) as t)
       select jsonb_agg(t) into result from t where t is not null;
    END IF;
    IF jsonb_typeof(json) = 'object' THEN
     -- TODO recursively call jsonb_trunc for every element
    END IF;
    IF result = '{}'::jsonb then
      result = null;
    end if;
    IF result = '[]'::jsonb then
      result = null;
    end if;
    return result;
END;
$$ LANGUAGE plpgsql;
