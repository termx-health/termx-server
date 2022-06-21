--liquibase formatted sql

--changeset kodality:tr-privilege-def-resources
drop trigger if exists tr_privilege_def_resources on auth.privilege;
create trigger tr_privilege_def_resources after insert or update on auth.privilege for each row execute procedure ftr_def_privilege_resources();
--rollback drop trigger if exists tra_invoice_treatment_save_history on invoice;


