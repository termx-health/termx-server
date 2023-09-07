--liquibase formatted sql

--changeset kodality:concept-defined-properties-3
with t (name, kind, type, uri, description) as (values ('status', 'property', 'code', 'http://hl7.org/fhir/concept-properties#status', '{"en": "Status"}'::jsonb),
                                                 ('inactive', 'property', 'boolean', 'http://hl7.org/fhir/concept-properties#inactive', '{"en": "Inactive"}'::jsonb),
                                                 ('effectiveDate', 'property', 'dateTime', 'http://hl7.org/fhir/concept-properties#effectiveDate', '{"en": "Effective Date"}'::jsonb),
                                                 ('deprecationDate', 'property', 'dateTime', 'http://hl7.org/fhir/concept-properties#deprecationDate', '{"en": "Deprecation Date"}'::jsonb),
                                                 ('retirementDate', 'property', 'dateTime', 'http://hl7.org/fhir/concept-properties#retirementDate', '{"en": "Retirement Date"}'::jsonb),
                                                 ('notSelectable', 'property', 'boolean', 'http://hl7.org/fhir/concept-properties#notSelectable', '{"en": "Not Selectable"}'::jsonb),
                                                 ('parent', 'property', 'code', 'http://hl7.org/fhir/concept-properties#parent', '{"en": "Parent"}'::jsonb),
                                                 ('child', 'property', 'code', 'http://hl7.org/fhir/concept-properties#child', '{"en": "Child"}'::jsonb),
                                                 ('partOf', 'property', 'code', 'http://hl7.org/fhir/concept-properties#partOf', '{"en": "Part Of"}'::jsonb),
                                                 ('groupedBy', 'property', 'code', null, '{"en": "Grouped By"}'::jsonb),
                                                 ('classifiedWith', 'property', 'code', null, '{"en": "Classified With"}'::jsonb),
                                                 ('synonym', 'property', 'code', 'http://hl7.org/fhir/concept-properties#synonym', '{"en": "Synonym"}'::jsonb),
                                                 ('comment', 'property', 'string', 'http://hl7.org/fhir/concept-properties#comment', '{"en": "Comment"}'::jsonb),
                                                 ('itemWeight', 'property', 'decimal', 'http://hl7.org/fhir/concept-properties#itemWeight', '{"en": "Item Weight"}'::jsonb),

                                                 ('alternate', 'property', 'code', 'http://hl7.org/fhir/StructureDefinition/codesystem-alternate', '{"en": "Alternate"}'::jsonb),
                                                 ('comments', 'property', 'string', 'http://hl7.org/fhir/StructureDefinition/codesystem-concept-comments', '{"en": "Concept Comment"}'::jsonb),
                                                 ('replacedby', 'property', 'code', 'http://hl7.org/fhir/StructureDefinition/codesystem-replacedby', '{"en": "Replaced By"}'::jsonb),
                                                 ('conceptOrder', 'property', 'integer', 'http://hl7.org/fhir/StructureDefinition/codesystem-conceptOrder', '{"en": "Concept Order"}'::jsonb),

                                                 ('display', 'designation', 'string', 'http://terminology.hl7.org/CodeSystem/designation-usage|display', '{"en": "Display"}'::jsonb),
                                                 ('definition', 'designation', 'string', null, '{"en": "Definition"}'::jsonb)

)
   , e as (select t.*, (exists(select 1 from terminology.defined_entity_property dep where t.name = dep.name)) as pexists from t)
   , inserted as (insert into terminology.defined_entity_property(name, kind, type, uri, description) select e.name, e.kind, e.type, e.uri, e.description from e where e.pexists = false)
   , updated as (update terminology.defined_entity_property dep set kind = e.kind, type = e.type, uri = e.uri, description = e.description from e where e.pexists = true and e.name = dep.name)
select 1;
--rollback select 1;
