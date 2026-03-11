CREATE OR REPLACE FUNCTION jsonb_diff(json1 jsonb, json2 jsonb)
RETURNS JSONB AS $$
DECLARE
result jsonb;
  v record;
BEGIN
   result = json1;
FOR v IN SELECT * FROM jsonb_each(json2) LOOP
    IF result @> jsonb_build_object(v.key,v.value)
        THEN result = result - v.key;
ELSIF result ? v.key THEN CONTINUE;
ELSE
        result = result || jsonb_build_object(v.key,'null');
END IF;
END LOOP;
RETURN result;
END;
$$ LANGUAGE plpgsql;
