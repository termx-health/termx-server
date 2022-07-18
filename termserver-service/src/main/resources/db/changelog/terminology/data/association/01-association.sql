--liquibase formatted sql

--changeset kodality:concept-map-equivalence
with t (code, association_kind, forward_name, reverse_name, directed, description) as (values ('relatedto', 'concept-map-equivalence', 'Related To', null, true,
                                                                                               'The concepts are related to each other, and have at least some overlap in meaning, but the exact relationship is not known.'),
                                                                                           ('equivalent', 'concept-map-equivalence', 'Equivalent', null, true,
                                                                                            'The definitions of the concepts mean the same thing (including when structural implications of meaning are considered) (i.e. extensionally identical).'),
                                                                                           ('equal', 'concept-map-equivalence', 'Equal', null, true,
                                                                                            'The definitions of the concepts are exactly the same (i.e. only grammatical differences) and structural implications of meaning are identical or irrelevant (i.e. intentionally identical).'),
                                                                                           ('wider', 'concept-map-equivalence', 'Wider', null, true,
                                                                                            'The target mapping is wider in meaning than the source concept.'),
                                                                                           ('subsumes', 'concept-map-equivalence', 'Subsumes', null, true,
                                                                                            'The target mapping subsumes the meaning of the source concept (e.g. the source is-a target).'),
                                                                                           ('narrower', 'concept-map-equivalence', 'Narrower', null, true,
                                                                                            'The target mapping is narrower in meaning than the source concept. The sense in which the mapping is narrower SHALL be described in the comments in this case, and applications should be careful when attempting to use these mappings operationally.'),
                                                                                           ('specializes', 'concept-map-equivalence', 'Specializes', null, true,
                                                                                            'The target mapping specializes the meaning of the source concept (e.g. the target is-a source).'),
                                                                                           ('inexact', 'concept-map-equivalence', 'Inexact', null, true,
                                                                                            'The target mapping overlaps with the source concept, but both source and target cover additional meaning, or the definitions are imprecise and it is uncertain whether they have the same boundaries to their meaning. The sense in which the mapping is inexact SHALL be described in the comments in this case, and applications should be careful when attempting to use these mappings operationally.'),
                                                                                           ('unmatched', 'concept-map-equivalence', 'Unmatched', null, true,
                                                                                            'There is no match for this concept in the target code system.'),
                                                                                           ('disjoint', 'concept-map-equivalence', 'Disjoint', null, true,
                                                                                            'This is an explicit assertion that there is no mapping between the source and target concept.')
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
                                                                                           ('part-of', 'concept-map-equivalence', 'Part Of', null, true,
                                                                                            'Child elements list the individual parts of a composite whole (e.g. body site).'),
                                                                                           ('classified-with', 'concept-map-equivalence', 'Classified With', null, true,
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
