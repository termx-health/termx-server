CREATE OR REPLACE FUNCTION core.session_user()
  RETURNS text AS
$BODY$
declare 
  l_str text;
begin
  select core.string2null(current_setting('core.client_identifier')) into l_str; 
  return l_str;
exception when others then
  raise exception 'Session user is not initialized.' USING HINT = 'Please initialize user with command: select core.set_user(''YOUR_USER''); ';
  return null;
end;
$BODY$
LANGUAGE plpgsql VOLATILE;