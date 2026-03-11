create or replace function core.exec(p_sql text, p_ignore_error boolean default false) returns varchar language plpgsql volatile as 
$body$
declare
  rc bigint;
begin
  execute p_sql;
  GET DIAGNOSTICS rc = ROW_COUNT;
  return '+' || cast(rc as varchar);
exception when OTHERS then
  if p_ignore_error is true then 
    raise info 'Error ignored! Execution of "%" failed with SQLSTATE % and error message: "%"', p_sql, SQLSTATE, SQLERRM;
    return '-';
  else
    raise exception 'Execution of "%" failed with SQLSTATE % and error message: "%"', p_sql, SQLSTATE, SQLERRM;
    return SQLSTATE;
  end if;
end;
$body$;
