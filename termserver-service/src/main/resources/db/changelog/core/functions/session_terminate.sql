create or replace function core.session_terminate(
 in_db               varchar default current_database(),  -- if null then session to all databases will be terminated
 in_kill_own_session boolean default false                -- if false then don't kill my own connection!
) returns boolean
as
$body$
declare 
 r boolean;
begin
-- terminate user sessions to propagation database 
 SELECT pg_terminate_backend(t.pid) into r
   FROM pg_stat_activity t
  WHERE t.datname = coalesce(in_db,t.datname) 
    AND (in_kill_own_session is true or (in_kill_own_session is false and t.pid <> pg_backend_pid()));

 return r;
end;
$body$
language plpgsql;
