--liquibase formatted sql

--changeset kodality:snomed_translation
drop table if exists snomed.snomed_translation;
create table snomed.snomed_translation (
    id                  bigserial           not null primary key,
    description_id      text,
    concept_id          text                not null,
    module              text                not null,
    language            text                not null,
    term                text                not null,
    type                text                not null,
    acceptability       text                not null,
    status              text                not null,
    sys_created_at      timestamp           not null,
    sys_created_by      text                not null,
    sys_modified_at     timestamp           not null,
    sys_modified_by     text                not null,
    sys_status          char(1) default 'A' not null collate "C",
    sys_version         int                 not null
);
create unique index snomed_translation_ukey on snomed.snomed_translation (description_id) where (sys_status = 'A');
create index snomed_translation_concept_idx on snomed.snomed_translation (concept_id);

select core.create_table_metadata('snomed.snomed_translation');
--rollback drop table if exists snomed.snomed_translation;

--changeset kodality:snomed_translation-branch
alter table snomed.snomed_translation add column branch text;
--
