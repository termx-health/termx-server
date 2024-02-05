--liquibase formatted sql

--changeset kodality:checklist_rules-1
with vals (code, title, description, verification, target, resource_type) as (values
          ('D1',
           '{"en": "A formal methodology for content discovery and expansion is defined", "et": "Sisu avastamiseks ja laiendamiseks on määratletud formaalne metoodika "}'::jsonb,
           '{"en": "Formal, explicit, and reproducible methods are created and employed to identify and address content gaps with the same level of rigor in order to facilitate content discovery and expansion.", "et": "Sisu lünkade tuvastamiseks ja täitmiseks on arendatud formaalsed, selged ja korduvad meetodid sarnase rangusega, mis on kasutusel sisu avastamiseks ja laiendamiseks."}'::jsonb,
           'human', 'design', 'CodeSystem'),

          ('D2',
           '{"en": "Identification codes in code systems have no semantic meaning", "et": "Koodisüsteemide identifikatsiooni koodidel puudub semantiline tähendus"}'::jsonb,
           '{"en": "The code is automatically applied to the concept. The code does not include the term or parent code in the hierarchy. The code may have a client namespace and checksum for multi-authoring process.", "et": "Kood rakendatakse mõistele automaatselt. Kood ei sisalda terminit ega vanem-koodi hierarhias. Koodil võib olla kliendi nimeruum ja kontrollsumma mitme autoriprotsessi toetamiseks."}'::jsonb,
           'software', 'design', 'CodeSystem'),

          ('D3',
           '{"en": "The code system uses formal definitions", "et": "Koodisüsteem kasutab formaalseid määratlusi"}'::jsonb,
           '{"en": "Precise description of concept''s formal definition removes ambiguity and provides a clear understanding of its meaning. Definitions are often expressed through relationships to other concepts.", "et": "Mõiste formaalse definitsiooni täpne kirjeldus kõrvaldab mitmetähenduslikkuse ja tagab selle tähenduse selge mõistmise. Definitsioone väljendatakse sageli suhetes teiste mõistetega."}'::jsonb,
           'software', 'design', 'CodeSystem'),

          ('D4',
           '{"en": "The code system design predicts changes in medical terminology and knowledge", "et": "Koodisüsteemi disain arvestab meditsiiniterminoloogia- ja teadmiste arenguga"}'::jsonb,
           '{"en": "The goal is to ensure the adaptability of terminology maintainers to both content and structural developments. Changes are accompanied by action-based explanations through detailed descriptions, where best practices are collectively understood (addition, clarification, correction, pre-coordination, etc.), and undesirable practices are avoided (redundancy, significant name changes, code reuse, etc.)", "et": "Eesmärgiks on tagada terminoloogia haldajate kohanemisvõimekus sisu kui ka struktuuri arengu suhtes.  Muudatustel on tegevuspõhine selgitus läbi detailsete kirjelduste, kus head tavad on mõistetud ühiselt (lisamine, täpsustus, korrigeerimine, eelkoordineerimine jne) ning halvad tavad välditud (korduskasutus, suured nimemuudatused, koodivahetus jne)."}'::jsonb,
           'software', 'design', 'CodeSystem'),

          ('C1',
           '{"en": "Every concept has at least one designation", "et": "Iga mõiste omab vähemalt ühte tähist"}'::jsonb,
           '{"en": "At least one term is needed to describe the concept’s meaning to human users.", "et": "Vähemalt üks termin on vajalik, et kirjeldada mõiste tähendust inimkasutajatele."}'::jsonb,
           'software', 'concept', 'CodeSystem'),

          ('C2',
           '{"en": "Every active concept has a preferred term", "et": "Iga aktiivne mõiste omab eelistatud terminit"}'::jsonb,
           '{"en": "It is advisable to choose one term as the preferred term if multiple terms are used for a concept.", "et": "Soovitatav valida üks termin eelistatud terminina, kui ühe mõiste jaoks kasutatakse mitut terminit."}'::jsonb,
           'software', 'concept', 'CodeSystem'),

          ('C3',
           '{"en": "Each active preferred term in the vocabulary is unique", "et": "Sõnavaras on iga aktiivne eelistatud termin unikaalne"}'::jsonb,
           '{"en": "The meanings represent only one term (\"non-redundancy\").", "et": "Tähendused esindavad ainult ühte terminit (\"mitte-üleliigsus\")."}'::jsonb,
           'software', 'concept', 'CodeSystem'),

          ('C4',
           '{"en": "Each concept in the vocabulary has a single, coherent meaning", "et": "Iga mõiste sõnavaras omab ühtset ja seostatavat tähendust"}'::jsonb,
           '{"en": "Concept orientation requires terms to have a single meaning and meanings to correspond to a single term. The meaning of a concept can vary depending on the context.", "et": "Mõiste orientatsioon eeldab, et terminitel oleks üksainus tähendus ja tähendused vastaksid ühele termile. Mõiste tähendus võib sõltuvalt kontekstist erineda."}'::jsonb,
           'human', 'concept', 'CodeSystem'),

          ('C5',
           '{"en": "All description are concise", "et": "Kõik kirjeldused on lakoonilised"}'::jsonb,
           '{"en": "Descriptions should be concise. Well-written descriptions should only include necessary information for proper placement in the concept system. Extra information or examples should be in a note. This extra information could include key non-essential traits or a list of typical objects within the concept''s scope.", "et": "Kirjeldused peaksid olema lühikesed. Kirjeldustes peaks olema ainult vajalik teave õige paigutuse tagamiseks mõistesüsteemis. Lisateave või näited peaksid olema märkuses. Täiendav teave võib hõlmata omadusi või loetelu mõiste ulatusele (a)tüüpilistest objektidest."}'::jsonb,
           'human', 'concept', 'CodeSystem'),

          ('C6',
           '{"en": "The meaning of the modified concept is still the same", "et": "Muudetud mõiste tähendus jääb samaks"}'::jsonb,
           '{"en": "The meaning of a concept, once created, is inviolate/permanent.", "et": "Mõiste tähendus, kui see on loodud, on puutumatu/püsiv."}'::jsonb,
           'human', 'concept', 'CodeSystem'),

          ('C7',
           '{"en": "The concept code and term should follow formatting conventions", "et": "Mõiste kood ja termin peaksid järgima vormindamise tavasid"}'::jsonb,
           '{"en": "The concept code and term must consist of Unicode characters, with no leading or trailing whitespaces. Double spaces in content are forbidden. Strings cannot include Unicode characters below 32, except for tabs, carriage return, and line feed. The code must not have any ''grave accent'' characters (Strict rules). Strings must not include tabs, newlines, or the characters @, $, #, \\.) (warnings)", "et": "Mõiste kood ja termin peavad koosnema Unicode''i märkidest, ilma juht- ega järeltühikuta. Topelttühikud sisus on keelatud. Tekstid ei tohi sisaldada Unicode''i märke alla 32, välja arvatud vahekaardid, vahetusringid ja reavahetused. Kood ei tohi sisaldada ühtegi akuut/graavis tüüpi märki. Tekstid ei tohi sisaldada tabulaatoreid, reavahetusi ega märke @, $, #, \\."}'::jsonb,
           'software', 'concept', 'CodeSystem'),

          ('C8',
           '{"en": "The term must be grammatically correct", "et": "Termin peab olema grammatiliselt korrektne"}'::jsonb,
           '{"en": "Correct grammar is crucial for clarity, credibility, and perception.", "et": "Korrektselt kasutatud grammatika on oluline selguse, usaldusväärsuse ja ühtse arusaamise jaoks."}'::jsonb,
           'human', 'concept', 'CodeSystem'),

          ('R1',
           '{"en": "All formal definitions are unique", "et": "Kõik formaalsed määratlused on unikaalsed"}'::jsonb,
           '{"en": "Collection of properties and values associated with one concept is distinct from the collection of properties and values associated with any other concept.", "et": "Ühe mõiste omaduste ja väärtuste kogu ei kordu ühegi teise mõiste omaduste ja väärtuste koguga."}'::jsonb,
           'software', 'release', 'CodeSystem'),

          ('R2',
           '{"en": "The code system has no terms indicating \"Not Elsewhere Classified\"", "et": "Koodisüsteem ei sisalda termineid, mis tähendavad \"Muu mujal liigitamata\""}'::jsonb,
           '{"en": "Controlled vocabularies must not include terms that encode unique information. These terms lack a formal definition and can only be defined by what they are not. Their meaning changes with the addition or removal of other concepts in the code system.", "et": "Kontrollitud sõnavarad ei tohiks sisaldada termineid, mis kodeerivad ainulaadset teavet. Need terminid ei ole formaalselt defineeritud ja neid saab defineerida ainult selle järgi, mida nad ei ole. Nende tähendus muutub teiste mõistete lisamise või eemaldamisega koodisüsteemist."}'::jsonb,
           'human', 'release', 'CodeSystem'),

          ('R3',
           '{"en": "The terminology was created independently of the specific contexts in which it would be used", "et": "Terminoloogia loodi sõltumatult konkreetsetest kontekstidest, kus seda kasutatakse"}'::jsonb,
           '{"en": "Terminology requires grammar to be flexible, extensible, and comprehensive. Restrictions are necessary for vocabulary to support operations like predictive data entry, natural language processing, and aggregation of patient records.", "et": "Terminoloogia nõuab grammatikalt paindlikkust, laiendatavust ja põhjalikkust. Piirangud on vajalikud, et sõnavara toetaks toiminguid nagu ennustav andmesisestus, loomuliku keele töötlemine ja patsiendiandmete kogumine."}'::jsonb,
           'human', 'release', 'CodeSystem'),

          ('R4',
           '{"en": "The code system is consistent", "et": "Koodisüsteem on järjekindel"}'::jsonb,
           '{"en": "The relations are valid and don’t contain inactivated components.", "et": "Suhted on kehtivad ja ei sisalda kehtetuid komponente."}'::jsonb,
           'software', 'release', 'CodeSystem')
),
ins as (
insert into sys.checklist_rule(code, title, description, active, type, verification, severity, target, resource_type)
select code, title, description, true, 'system', verification, 'error', target, resource_type from vals v
where not exists(select 1 from sys.checklist_rule where code = v.code and sys_status = 'A')
returning id
),
upd as (
update sys.checklist_rule
set title = v.title, description = v.description, verification = v.verification, target = v.target, resource_type = v.resource_type
from vals v
where v.code = checklist_rule.code and checklist_rule.sys_status = 'A'
returning id
)
select * from ins, upd;
--
