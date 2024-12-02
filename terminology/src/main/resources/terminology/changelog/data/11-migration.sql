--liquibase formatted sql

--changeset termx:publisher-3
update terminology.entity_property_value v
   set value='"https://termx.org/fhir"'
 where value='"https://kodality.org/fhir"';

update terminology.designation d
   set name='TermX'
 where exists(
          select 1
            from terminology.code_system_entity_version ev
                 inner join terminology.concept c on c.id = ev.code_system_entity_id and c.code_system='publisher' and c.code='kts'
            where d.code_system_entity_version_id = ev.id)
    and not exists(
          select 1 from terminology.concept
           where code='termx' and code_system='publisher');

update terminology.code_system
   set uri = replace(uri, 'http://tx.hl7.ee', 'https://termx.org/fhir')
 where uri like '%tx.hl7.ee%';

update terminology.value_set
   set uri = replace(uri, 'http://tx.hl7.ee', 'https://termx.org/fhir')
 where uri like '%tx.hl7.ee%';

update terminology.code_system
   set publisher = 'termx'
 where publisher='kts';

update terminology.value_set
   set publisher = 'termx'
 where publisher='kts';

update terminology.code_system
   set publisher = 'termx'
 where id='publisher';
--rollback select 1;


--changeset termx:fix-versions-2
update terminology.code_system_version
   set version = '5.0.0'
 where version='5.0.0-cibuild';

update terminology.code_system_version
   set version = '6.0.0'
 where version='6.0.0-cibuild';

update terminology.value_set_version
   set version = '5.0.0'
 where version='5.0.0-cibuild';

 update terminology.value_set_version
   set version = '6.0.0'
 where version='6.0.0-cibuild';
--rollback select 1;

--changeset termx:kts-to-termx-3
 update terminology.code_system_entity_version ev
   set code = 'termx'
 where code_system='publisher' and code='kts'
   and not exists(
      select 1 from terminology.concept
       where code='termx' and code_system='publisher');

update terminology.concept
   set code = 'termx'
 where code='kts' and code_system='publisher'
   and not exists(
      select 1 from terminology.concept
       where code='termx' and code_system='publisher');
--rollback select 1;