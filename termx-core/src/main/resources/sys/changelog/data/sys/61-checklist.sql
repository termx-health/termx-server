--changeset kodality:checklist_rules
with t(code, title, description, verification, target, resource_type) as (values
          ('D1',
           '{"en": "A formal methodology for content discovery and expansion is defined"}'::jsonb,
           '{"en": "Formal, explicit, reproducible methods for recognizing and filling gaps in the content of similar rigour are developed and used for content discovery and expansion."}'::jsonb,
           'human', 'design', 'CodeSystem'),

          ('D2',
           '{"en": "Identification codes in code systems have no semantic meaning"}'::jsonb,
           '{"en": "The code is applied to the concept automatically. The code does not contain the term or parent code in the hierarchy. The code may have a client namespace and checksum to support a multi-authoring process."}'::jsonb,
           'software', 'design', 'CodeSystem'),

          ('D3',
           '{"en": "The code system uses formal definitions"}'::jsonb,
           '{"en": "A formal definition of a concept is a precise and rigorous description of that concept, using precise reasoning to remove ambiguity and provide a clear understanding of what the concept means. Usually, these definitions are expressed as some collection of relationships to other concepts in the vocabulary."}'::jsonb,
           'software', 'design', 'CodeSystem'),

          ('D4',
           '{"en": "The design of a code system anticipates the evolution of medical terminology along with the evolution of medical knowledge"}'::jsonb,
           '{"en": "An important desideratum is that those charged with maintaining the terminology must accommodate the graceful evolution of their content and structure. This can be accomplished through clear, detailed descriptions of what changes occur and why so that good reasons for change (such as simple addition, refinement, precoordination, disambiguation, obsolescence, discovered redundancy, and minor name changes) can be understood. Bad reasons (such as redundancy, major name changes, code reuse, and changed codes) can be avoided."}'::jsonb,
           'software', 'design', 'CodeSystem'),

          ('C1',
           '{"en": "Every concept has at least one designation"}'::jsonb,
           '{"en": "At least one term is needed to describe the concept’s meaning to human users."}'::jsonb,
           'software', 'concept', 'CodeSystem'),

          ('C2',
           '{"en": "Every active concept has a preferred term"}'::jsonb,
           '{"en": "If several terms are used to designate a concept, it is recommended that only one term be selected as the preferred one."}'::jsonb,
           'software', 'concept', 'CodeSystem'),

          ('C3',
           '{"en": "Each active preferred term in the vocabulary is unique"}'::jsonb,
           '{"en": "The meanings correspond to no more than one term (“nonredundancy”)."}'::jsonb,
           'software', 'concept', 'CodeSystem'),

          ('C4',
           '{"en": "Each concept in the vocabulary has a single, coherent meaning"}'::jsonb,
           '{"en": "Concept orientation means that terms must correspond to at least one (“nonvagueness”) and no more than one meaning (“nonambiguity”), and that meanings correspond to no more than one term (“nonredundancy”). Concept meaning might vary, depending on its appearance in a context."}'::jsonb,
           'human', 'concept', 'CodeSystem'),

          ('C5',
           '{"en": "All description are concise"}'::jsonb,
           '{"en": "Descriptions shall be as brief as possible. Carefully written descriptions should contain only information required to place the concept correctly in the concept system. Any additional information or examples should be placed in a note. Such additional information could be, for example, the most important inessential characteristics or a list of typical objects included in the extension of the concept."}'::jsonb,
           'human', 'concept', 'CodeSystem'),

          ('C6',
           '{"en": "The meaning of the modified concept is still the same"}'::jsonb,
           '{"en": "The meaning of a concept, once created, is inviolate/permanent."}'::jsonb,
           'human', 'concept', 'CodeSystem'),

          ('C7',
           '{"en": "The concept code and term should follow formatting conventions"}'::jsonb,
           '{"en": "Strict rules: The concept code and term should be a sequence of Unicode characters. Leading and trailing whitespaces should be trimmed. Content containing double spaces is not allowed. Strings should not contain Unicode character points below 32, except for tabs, carriage return and line feed. The code should not contain any “grave accent“ characters. Warnings: Strings should not contain tabs, newlines, or characters @, $, #, \\."}'::jsonb,
           'software', 'concept', 'CodeSystem'),

          ('C8',
           '{"en": "The term must be grammatically correct"}'::jsonb,
           '{"en": "Using correct grammar is essential for clarity, credibility and perception."}'::jsonb,
           'human', 'concept', 'CodeSystem'),

          ('R1',
           '{"en": "All formal definitions are unique"}'::jsonb,
           '{"en": "Every collection of properties and values of one concept does not duplicate the collection of properties and values of any other concept."}'::jsonb,
           'software', 'release', 'CodeSystem'),

          ('R2',
           '{"en": "The code system does not contain any terms that mean “Not Elsewhere Classified“"}'::jsonb,
           '{"en": "Controlled vocabularies should reject the use of terms that can be used to encode information not represented by other existing terms. The problem with such terms is that they can never have a formal definition other than one of exclusion - that is, the definition can only be based on knowledge of the rest of the concepts in the vocabulary. And their semantic meaning will change with the addition or removal of concepts in the code system."}'::jsonb,
           'human', 'release', 'CodeSystem'),

          ('R3',
           '{"en": "The terminology was created independently of the specific contexts in which it would be used"}'::jsonb,
           '{"en": "Terminology could never be truly flexible, extensible and comprehensive without grammar to define how it should be used. Such limitations are needed for the vocabulary to support operations such as predictive data entry, natural language processing, and aggregation of patient records; An example of context independent concept is the disorder “Myocardial infarction”, An example of a concept representing the context is  “Person in the family”, and an example context a dependent concept is “Family history of myocardial infarction” which refers to the associated finding “Myocardial infarction” and subject relationship context “Person in the family”."}'::jsonb,
           'human', 'release', 'CodeSystem'),

          ('R4',
           '{"en": "The code system is consistent"}'::jsonb,
           '{"en": "The relations are valid and don’t contain inactivated components."}'::jsonb,
           'software', 'release', 'CodeSystem')
)
insert into sys.checklist_rule(code, title, description, active, type, verification, severity, target, resource_type)
select code, title, description, true, 'system', verification, 'error', target, resource_type from t
where not exists(select 1 from sys.checklist_rule where code = t.code and sys_status = 'A')
--
