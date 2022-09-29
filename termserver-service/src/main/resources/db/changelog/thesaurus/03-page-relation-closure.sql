drop materialized view if exists thesaurus.page_relation_closure;
create materialized view thesaurus.page_relation_closure
as
with tree as (
    select p.id, pr.source_id as parent_id
    from thesaurus.page p
             left outer join thesaurus.page_relation pr on pr.target_id = p.id and pr.sys_status = 'A'
    where p.sys_status = 'A'
),
    hier as (
        with recursive rec as (
            select p.id, p.parent_id, ('.'::text || p.id) || '.'::text as path, 0 as depth
            from tree p
            where p.parent_id is null
            union all
            select ch.id, ch.parent_id, (p.path || ch.id) || '.'::text, p.depth + 1
            from tree ch
                     join rec p on ch.parent_id = p.id
        )
        select *
        from rec
    )
select h1.id as parent_id, h2.id as child_id, h2.depth - h1.depth as distance, h2.path as path
from hier h1, hier h2
where h2.path ~~ (h1.path || '%'::text);


