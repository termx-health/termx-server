--liquibase formatted sql

--changeset termx:liquibase-01
update public.databasechangelog
   set author = 'termx'
 where author <> 'termx' and id in (
  'snomed-ct','snomed-module','v3-ConceptStatus','ucum','publication-status','codesystem-content-mode','v3-ietf3066', 'languages','concept-property-type',
  'contact-point-system','contact-point-use','filter-operator','namingsystem-identifier-type','namingsystem-type',
  'orpha-classification-level','orpha-disorder-type','orpha-flag-value','orpha-total-status','publisher',
  'version-algorithm','iso3166-1','iso3166-2','m49','issue-severity','definition-topic','definition-use',
  'termx-resource-configuration','usage-context-type','jurisdiction'
);
--rollback select 1;
