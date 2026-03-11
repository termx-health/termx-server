--liquibase formatted sql

--changeset kodality:seq_id
create sequence if not exists core.seq_id;
--
