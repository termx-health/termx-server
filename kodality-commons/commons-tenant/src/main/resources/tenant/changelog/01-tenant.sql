--liquibase formatted sql

--changeset kodality:tenant
create table if not exists core.tenant (
  id                    text not null,
  name                  text not null,
  constraint tenant_pkey primary key (id)
);
--
