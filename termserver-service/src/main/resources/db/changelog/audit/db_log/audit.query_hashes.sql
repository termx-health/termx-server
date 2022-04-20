--liquibase formatted sql

--changeset kodality:audit.query_hashes
drop table if exists audit.query_hashes; 
CREATE TABLE audit.query_hashes (
    id bigserial primary key,
    hash text,
    query text
);

CREATE INDEX query_hashes_hash ON audit.query_hashes(hash);
--rollback drop table audit.query_hashes;

