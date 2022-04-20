CREATE OR REPLACE FUNCTION core.get_setting(text)
  RETURNS text AS
$BODY$
declare 
  l_str text;
begin
  select core.string2null(current_setting($1, true)) into l_str;
  return l_str;
exception when others then
  return null;
end;
$BODY$
LANGUAGE plpgsql VOLATILE;
