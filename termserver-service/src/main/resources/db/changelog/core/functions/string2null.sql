CREATE OR REPLACE FUNCTION core.string2null(varchar) 
RETURNS varchar AS 
$BODY$ 
BEGIN 
  IF trim($1) = '' THEN 
    RETURN null; 
  ELSE
    RETURN rtrim($1);
  END IF;
END; 
$BODY$ 
LANGUAGE 'plpgsql' IMMUTABLE; 
