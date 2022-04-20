--liquibase formatted sql

--changeset kodality:s_entity
create sequence if not exists s_entity;
--rollback drop sequence s_entity;
