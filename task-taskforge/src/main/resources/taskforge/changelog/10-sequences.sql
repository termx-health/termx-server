--liquibase formatted sql

--changeset termx:sequences-1
with vals(code, description, restart, pattern, start_from) as (values
('task', 'Task number', 'never', '[NNNN]', '1')
)
  insert into core.sys_sequence (code, description, restart, pattern, start_from)
  select * from vals
  where not exists(select 1 from core.sys_sequence s where s.code=vals.code);
--
