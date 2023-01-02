create or replace view terminology.concept_order
as
select csev.code_system, csev.code, epv.value::int as ordering
  from terminology.code_system_entity_version csev
       inner join terminology.entity_property_value epv on epv.code_system_entity_version_id = csev.id and epv.sys_status='A'
       inner join terminology.entity_property epo on epo.id = epv.entity_property_id and epo.name='order'
 where csev.sys_status='A'
   and csev.status<>'retired'
;
