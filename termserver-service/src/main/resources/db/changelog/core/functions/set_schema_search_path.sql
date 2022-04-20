CREATE OR REPLACE FUNCTION core.set_schema_search_path(p_user text, p_schema_name varchar,p_action varchar default 'END') RETURNS varchar AS $$
DECLARE
  l_user oid;
  l_search_path varchar;
  l_sql varchar;
BEGIN
  -- get current user oid
  select usesysid into l_user from pg_user where usename=(case when p_user is not null then p_user else current_user end);
  -- get search path for current user in current database
  select substr(param,ind+1) into l_search_path  
    from (
       select unnest(setconfig) param, strpos(unnest(setconfig),'=') ind from pg_db_role_setting where setrole=l_user 
    ) as t
   where substr(param,1,ind-1)='search_path'; 
  if l_search_path is null then 
    l_search_path = 'public';
  end if;

  if p_action='END' then
     l_search_path = l_search_path || ',' || trim(p_schema_name);
  elsif p_action='BEGIN' then
     l_search_path = trim(p_schema_name) || ',' || l_search_path;
  elsif p_action='REWRITE' then
     l_search_path = trim(p_schema_name);
  end if;
  l_sql = 'ALTER ROLE '|| (case when p_user is not null then p_user else current_user end) || ' IN DATABASE ' || current_database() || ' SET search_path = ' || l_search_path;
  PERFORM core.exec(l_sql);
  
  RETURN l_sql;
END;
$$ LANGUAGE plpgsql;
