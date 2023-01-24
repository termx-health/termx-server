--liquibase formatted sql

--changeset kodality:value_set_fix_fk
ALTER TABLE terminology.value_set_version DROP CONSTRAINT value_set_version_value_set_fk;
ALTER TABLE terminology.value_set_version ADD CONSTRAINT value_set_version_value_set_fk FOREIGN KEY (value_set) REFERENCES terminology.value_set(id) ON UPDATE CASCADE;

ALTER TABLE terminology.value_set_version_rule DROP CONSTRAINT value_set_version_rule_value_set_fk;
ALTER TABLE terminology.value_set_version_rule ADD CONSTRAINT value_set_version_rule_value_set_fk FOREIGN KEY (value_set) REFERENCES terminology.value_set(id) ON UPDATE CASCADE;
--rollback select 1;

--changeset kodality:code_system_fix_fk
ALTER TABLE terminology.code_system DROP CONSTRAINT code_system_base_code_system_fk;
ALTER TABLE terminology.code_system ADD CONSTRAINT code_system_base_code_system_fk FOREIGN KEY (base_code_system) REFERENCES terminology.code_system(id) ON UPDATE CASCADE;

ALTER TABLE terminology.code_system_version DROP CONSTRAINT code_system_version_code_system_fk;
ALTER TABLE terminology.code_system_version ADD CONSTRAINT code_system_version_code_system_fk FOREIGN KEY (code_system) REFERENCES terminology.code_system(id) ON UPDATE CASCADE;

ALTER TABLE terminology.code_system_entity DROP CONSTRAINT code_system_entity_code_system_fk;
ALTER TABLE terminology.code_system_entity ADD CONSTRAINT code_system_entity_code_system_fk FOREIGN KEY (code_system) REFERENCES terminology.code_system(id) ON UPDATE CASCADE;

ALTER TABLE terminology.code_system_entity_version DROP CONSTRAINT code_system_entity_version_code_system_fk;
ALTER TABLE terminology.code_system_entity_version ADD CONSTRAINT code_system_entity_version_code_system_fk FOREIGN KEY (code_system) REFERENCES terminology.code_system(id) ON UPDATE CASCADE;

ALTER TABLE terminology.entity_property DROP CONSTRAINT entity_property_code_system_fk;
ALTER TABLE terminology.entity_property ADD CONSTRAINT entity_property_code_system_fk FOREIGN KEY (code_system) REFERENCES terminology.code_system(id) ON UPDATE CASCADE;

ALTER TABLE terminology.concept DROP CONSTRAINT concept_code_system_fk;
ALTER TABLE terminology.concept ADD CONSTRAINT concept_code_system_fk FOREIGN KEY (code_system) REFERENCES terminology.code_system(id) ON UPDATE CASCADE;

ALTER TABLE terminology.value_set_version_rule DROP CONSTRAINT value_set_version_rule_code_system_fk;
ALTER TABLE terminology.value_set_version_rule ADD CONSTRAINT value_set_version_rule_code_system_fk FOREIGN KEY (code_system) REFERENCES terminology.code_system(id) ON UPDATE CASCADE;

ALTER TABLE terminology.code_system_association DROP CONSTRAINT cs_association_code_system_fk;
ALTER TABLE terminology.code_system_association ADD CONSTRAINT cs_association_code_system_fk FOREIGN KEY (code_system) REFERENCES terminology.code_system(id) ON UPDATE CASCADE;

ALTER TABLE terminology.code_system_supplement DROP CONSTRAINT code_system_supplement_code_system_fk;
ALTER TABLE terminology.code_system_supplement ADD CONSTRAINT code_system_supplement_code_system_fk FOREIGN KEY (code_system) REFERENCES terminology.code_system(id) ON UPDATE CASCADE;

ALTER TABLE terminology.naming_system DROP CONSTRAINT naming_system_code_system_fk;
ALTER TABLE terminology.naming_system ADD CONSTRAINT naming_system_code_system_fk FOREIGN KEY (code_system) REFERENCES terminology.code_system(id) ON UPDATE CASCADE;
--rollback select 1;
