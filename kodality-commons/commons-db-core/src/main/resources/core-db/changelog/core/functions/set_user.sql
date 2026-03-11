CREATE OR REPLACE FUNCTION core.set_user(in_user varchar default current_user)
  RETURNS text AS
$BODY$
declare 
  l_str text;
begin
  SELECT set_config('core.client_identifier', in_user, false) into l_str; 
  return l_str;
end;
$BODY$
LANGUAGE plpgsql VOLATILE;