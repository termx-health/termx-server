create or replace function core.aclchk(p_id in bigint, p_tenant in text, p_access in text default 'view') returns boolean as
$$
  with acc (access, level) as (values ('owner', 1), ('edit', 2), ('consume', 3), ('view', 4))

  select exists (
    select 1 from core.acl where sys_status = 'A'
      and s_id = p_id and COALESCE(p_tenant, '--') = COALESCE(tenant, COALESCE(p_tenant, '--'))
      and access in (select access from acc where level <= (select level from acc where access = p_access))
  );
$$
language sql stable;
