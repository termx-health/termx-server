--liquibase formatted sql

--changeset kodality:sys_sequence
create table core.sys_sequence
(
  id                    bigserial not null,
  code                  text not null COLLATE "C",
  tenant                text,
  scope                 text,
  description			text,
  restart               text not null default 'never',
  pattern               text not null default '[N]',
  start_from            text not null default '0',
  
  sys_status            char(1) default 'A' not null,
  sys_version           integer not null,
  sys_created_at        timestamp not null,
  sys_created_by        text not null,
  sys_modified_at       timestamp,
  sys_modified_by       text,
  constraint sys_sequence_pkey primary key (id),
  constraint sys_sequence_ukey unique (code, scope),
  constraint sys_sequence_allowed_restarts CHECK (restart = ANY ('{daily,monthly,yearly,never}'::text[]))
);

comment on table core.sys_sequence       is 'Custom sequences';
comment on column core.sys_sequence.code is 'Sequence code identifier';
comment on column core.sys_sequence.scope is 'Reference to scoping entity, such as owner/issuer company or some specific item, for which sequence is defined';
comment on column core.sys_sequence.restart is 'Sequence restarting mode - dayly,monthly,yearly,never';
comment on column core.sys_sequence.pattern is 'Format for string sequences - parsed formats are [YY][YYYY][MM][DD][N..N]';

select core.create_table_metadata('core.sys_sequence');
--


--changeset kodality:sys_sequence_luv
create table core.sys_sequence_luv
(
  id               bigserial not null,
  sequence_id      bigint not null,
  period	       timestamp(0) null,
  luv              text not null,
  sys_modify_time  timestamp not null default localtimestamp,
  constraint sys_sequence_luv_pkey primary key (id),
  constraint sys_sequence_luv_ukey unique (sequence_id, period),
  constraint sys_sequence_luv_seq_fk foreign key (sequence_id) references core.sys_sequence (id)
);

comment on table core.sys_sequence_luv  is 'Last used values for sequences';
comment on column core.sys_sequence_luv.SEQUENCE_ID   is 'Reference to sequence';
comment on column core.sys_sequence_luv.PERIOD  is 'Period value, such as first day of year, month or day';
comment on column core.sys_sequence_luv.LUV  is 'Last used value for numeric sequence';
comment on column core.sys_sequence_luv.sys_modify_time  is 'Timestamp of last change of the sequence';

select core.create_table_metadata('core.sys_sequence_luv');
--

