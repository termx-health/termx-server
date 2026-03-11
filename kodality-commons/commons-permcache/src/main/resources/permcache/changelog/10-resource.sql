--liquibase formatted sql

--changeset '':permcache-resource
CREATE TABLE cache.resource (
    id bigserial primary key,
    resource_type text not null,
    resource_id   text not null,
    content jsonb not null,
    last_refreshed timestamptz not null
);

CREATE UNIQUE INDEX resource_resource_idx ON cache.resource(resource_type, resource_id);
--
