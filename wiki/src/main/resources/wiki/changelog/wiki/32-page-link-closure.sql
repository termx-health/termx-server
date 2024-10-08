--liquibase formatted sql

--changeset wiki:page_link_closure
drop materialized view if exists wiki.page_link_closure;
create materialized view wiki.page_link_closure
as
with tree as (
    select p.id, pl.source_id as parent_id
    from wiki.page p
             left outer join wiki.page_link pl on pl.target_id = p.id and pl.sys_status = 'A'
    where p.sys_status = 'A'
),
    hier as (
        with recursive rec as (
            select p.id, p.parent_id, ('.'::text || p.id) || '.'::text as path, 0 as depth
            from tree p
            where p.parent_id = p.id
            union all
            select ch.id, ch.parent_id, (p.path || ch.id) || '.'::text, p.depth + 1
            from tree ch
                     join rec p on ch.parent_id = p.id
            where ch.parent_id != ch.id
        )
        select *
        from rec
    )
select h1.id as parent_id, h2.id as child_id, h2.depth - h1.depth as distance, h2.path as path
from hier h1, hier h2
where h2.path ~~ (h1.path || '%'::text);
--
