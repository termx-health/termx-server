--liquibase formatted sql

--changeset kodality:concept-map-equivalence-1
with t (code, association_kind, forward_name, reverse_name, directed, description) as (values ('equivalent', 'concept-map-equivalence', 'Equivalent', null, true,
                                                                                               'The definitions of the concepts mean the same thing.'),
                                                                                           ('source-is-narrower-than-target', 'concept-map-equivalence', 'Source Is Narrower Than Target', null, true,
                                                                                            'The source concept is narrower in meaning than the target concept.'),
                                                                                           ('source-is-broader-than-target', 'concept-map-equivalence', 'Source Is Broader Than Target', null, true,
                                                                                            'The source concept is broader in meaning than the target concept.')
)
   , e as (select t.*, (exists(select 1 from terminology.association_type a where t.code = a.code)) as pexists from t)
   , inserted as (insert into terminology.association_type(code, association_kind, forward_name, reverse_name, directed, description) select e.code,
                                                                                                                                     e.association_kind,
                                                                                                                                     e.forward_name,
                                                                                                                                     e.reverse_name, e.directed,
                                                                                                                                     e.description
                                                                                                                                 from e
                                                                                                                                 where e.pexists = false)
   , updated
    as (update terminology.association_type a set association_kind = e.association_kind, forward_name = e.forward_name, reverse_name = e.reverse_name, directed = e.directed, description = e.description from e where e.pexists = true and e.code = a.code)
select 1;
--rollback select 1;



--changeset kodality:codesystem-hierarchy-meaning
with t (code, association_kind, forward_name, reverse_name, directed, description) as (values ('grouped-by', 'codesystem-hierarchy-meaning', 'Grouped By', null, true,
                                                                                               'No particular relationship between the concepts can be assumed, except what can be determined by inspection of the definitions of the elements (possible reasons to use this: importing from a source where this is not defined, or where various parts of the hierarchy have different meanings).'),
                                                                                           ('is-a', 'codesystem-hierarchy-meaning', 'Is-A', null, true,
                                                                                            'A hierarchy where the child concepts have an IS-A relationship with the parents - that is, all the properties of the parent are also true for its child concepts. Not that is-a is a property of the concepts, so additional subsumption relationships may be defined using properties or the [subsumes](extension-codesystem-subsumes.html) extension.'),
                                                                                           ('part-of', 'codesystem-hierarchy-meaning', 'Part Of', null, true,
                                                                                            'Child elements list the individual parts of a composite whole (e.g. body site).'),
                                                                                           ('classified-with', 'codesystem-hierarchy-meaning', 'Classified With', null, true,
                                                                                            'Child concepts in the hierarchy may have only one parent, and there is a presumption that the code system is a "closed world" meaning all things must be in the hierarchy. This results in concepts such as "not otherwise classified.".')
)
   , e as (select t.*, (exists(select 1 from terminology.association_type a where t.code = a.code)) as pexists from t)
   , inserted as (insert into terminology.association_type(code, association_kind, forward_name, reverse_name, directed, description) select e.code,
                                                                                                                                     e.association_kind,
                                                                                                                                     e.forward_name,
                                                                                                                                     e.reverse_name, e.directed,
                                                                                                                                     e.description
                                                                                                                                 from e
                                                                                                                                 where e.pexists = false)
   , updated
    as (update terminology.association_type a set association_kind = e.association_kind, forward_name = e.forward_name, reverse_name = e.reverse_name, directed = e.directed, description = e.description from e where e.pexists = true and e.code = a.code)
select 1;
--rollback select 1;
