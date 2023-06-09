--liquibase formatted sql

--changeset kodality:page-link_root_relations
insert into thesaurus.page_link(source_id, target_id, order_number)
select p.id, p.id, row_number() over ()
 from thesaurus.page p
 where p.sys_status = 'A' and not exists(select 1 from thesaurus.page_link pl where pl.sys_status= 'A' and pl.target_id = p.id)
--
