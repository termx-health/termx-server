--liquibase formatted sql

-- Backfill space-level language metadata (added in termx-core sys:space-ssg-metadata)
-- from the languages that already exist in each space's page content. Runs in the wiki
-- module so both sys.space and wiki.page_content are present. Only fills nulls, so it is
-- a no-op once a space owns explicit language settings.

--changeset wiki:space-language-backfill
update sys.space s set languages = sub.langs
from (
  select space_id, jsonb_agg(distinct lang order by lang) as langs
  from wiki.page_content
  where sys_status = 'A'
  group by space_id
) sub
where sub.space_id = s.id and s.languages is null;

-- Default language only when unambiguous: leave null (mdbook falls back to site.lang /
-- the first language) unless the space actually publishes English.
update sys.space
set default_language = 'en'
where default_language is null and languages @> '["en"]'::jsonb;
--
