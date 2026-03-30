--liquibase formatted sql

--changeset modeler:structure_definition_version-table
create table modeler.structure_definition_version (
  id                        bigserial not null primary key,
  structure_definition_id   bigint not null,
  version                   text,
  fhir_id                   text,
  content                   text not null,
  content_type              text not null,
  content_format             text not null,
  status                    text,
  release_date               timestamptz,
  description               text,
  sys_status                 char(1) default 'A' not null,
  sys_version                integer not null,
  sys_created_at             timestamptz not null,
  sys_created_by             text not null,
  sys_modified_at            timestamptz,
  sys_modified_by            text,
  constraint structure_definition_version_sd_fk foreign key (structure_definition_id) references modeler.structure_definition(id)
);
create unique index structure_definition_version_ukey on modeler.structure_definition_version (structure_definition_id, version) where (sys_status = 'A');
create index structure_definition_version_sd_idx on modeler.structure_definition_version(structure_definition_id);

select core.create_table_metadata('modeler.structure_definition_version');
--

--changeset modeler:structure_definition_version-migrate_data
-- Migrate existing rows: each row becomes header + one version (structure_definition_id = id)
insert into modeler.structure_definition_version (
  structure_definition_id, version, content, content_type, content_format, status, release_date,
  sys_status, sys_version, sys_created_at, sys_created_by, sys_modified_at, sys_modified_by
)
select id, version, content, content_type, content_format, 'draft', sys_created_at,
  sys_status, 1, sys_created_at, sys_created_by, sys_modified_at, sys_modified_by
from modeler.structure_definition
where sys_status = 'A';
--

--changeset modeler:structure_definition_version-drop_content_from_header
-- Drop content columns from header and add name, publisher
alter table modeler.structure_definition add column if not exists name text;
alter table modeler.structure_definition add column if not exists publisher text;
alter table modeler.structure_definition drop column if exists content;
alter table modeler.structure_definition drop column if exists content_type;
alter table modeler.structure_definition drop column if exists content_format;
alter table modeler.structure_definition drop column if exists version;
--

--changeset modeler:structure_definition_version-merge_duplicate_urls
-- Merge duplicate URLs: point versions to canonical header (min id per url), update package_version_resource, then remove duplicate headers
update modeler.structure_definition_version v
set structure_definition_id = m.canonical_id
from (
  select sd.id as old_id, (select min(sd2.id) from modeler.structure_definition sd2 where sd2.url = sd.url and sd2.sys_status = 'A') as canonical_id
  from modeler.structure_definition sd
  where sd.sys_status = 'A' and sd.id != (select min(sd3.id) from modeler.structure_definition sd3 where sd3.url = sd.url and sd3.sys_status = 'A')
) m
where v.structure_definition_id = m.old_id;

update sys.package_version_resource pvr
set resource_id = m.canonical_id::text
from (
  select sd.id as old_id, (select min(sd2.id) from modeler.structure_definition sd2 where sd2.url = sd.url and sd2.sys_status = 'A') as canonical_id
  from modeler.structure_definition sd
  where sd.sys_status = 'A' and sd.id != (select min(sd3.id) from modeler.structure_definition sd3 where sd3.url = sd.url and sd3.sys_status = 'A')
) m
where pvr.resource_type = 'structure-definition' and pvr.resource_id = m.old_id::text and pvr.sys_status = 'A';

delete from modeler.structure_definition
where sys_status = 'A' and id in (
  select id from modeler.structure_definition sd
  where sd.id != (select min(sd2.id) from modeler.structure_definition sd2 where sd2.url = sd.url and sd2.sys_status = 'A')
);
--

--changeset modeler:structure_definition_version-url_unique
-- Drop code unique, add url unique
drop index if exists modeler.structure_definition_code_ukey;
create unique index structure_definition_url_ukey on modeler.structure_definition (url) where (sys_status = 'A');
create index structure_definition_url_idx on modeler.structure_definition(url);
--

--changeset modeler:structure_definition_version-populate_version
-- 1) set version from content JSON where version is null
update modeler.structure_definition_version
set version = content::jsonb->>'version'
where sys_status = 'A' and version is null and content is not null and content::jsonb->>'version' is not null;

-- 2) set version using row_number for remaining nulls
update modeler.structure_definition_version v
set version = rn.v
from (
  select id, row_number() over (partition by structure_definition_id order by sys_modified_at) || '.0.0' as v
  from modeler.structure_definition_version
  where sys_status = 'A' and version is null
) rn
where v.id = rn.id;

-- 3) ensure uniqueness (drop and recreate to be safe)
drop index if exists modeler.structure_definition_version_ukey;
create unique index structure_definition_version_ukey on modeler.structure_definition_version (structure_definition_id, version) where (sys_status = 'A');
--

--changeset modeler:structure_definition-metadata_columns
alter table modeler.structure_definition add column if not exists title jsonb;
alter table modeler.structure_definition add column if not exists description jsonb;
alter table modeler.structure_definition add column if not exists identifiers jsonb;
alter table modeler.structure_definition add column if not exists contacts jsonb;
alter table modeler.structure_definition add column if not exists purpose jsonb;
alter table modeler.structure_definition add column if not exists hierarchy_meaning text;
alter table modeler.structure_definition add column if not exists experimental bool;
alter table modeler.structure_definition add column if not exists copyright jsonb;
alter table modeler.structure_definition add column if not exists use_context jsonb;
--

--changeset modeler:structure_definition_version-description_to_jsonb
alter table modeler.structure_definition_version alter column description type jsonb using case when description is not null then to_jsonb(description) else null end;
--

--changeset modeler:structure_definition_version-content_nullable
alter table modeler.structure_definition_version alter column content drop not null;
--
