--liquibase formatted sql

--changeset kodality:value_set_fix_fk-1
ALTER TABLE terminology.value_set_version DROP CONSTRAINT value_set_version_value_set_fk;
ALTER TABLE terminology.value_set_version ADD CONSTRAINT value_set_version_value_set_fk FOREIGN KEY (value_set) REFERENCES terminology.value_set(id) ON UPDATE CASCADE;

ALTER TABLE terminology.value_set_version_rule DROP CONSTRAINT value_set_version_rule_value_set_fk;
ALTER TABLE terminology.value_set_version_rule ADD CONSTRAINT value_set_version_rule_value_set_fk FOREIGN KEY (value_set) REFERENCES terminology.value_set(id) ON UPDATE CASCADE;

ALTER TABLE terminology.value_set_snapshot DROP CONSTRAINT value_set_snapshot_value_set_fk;
ALTER TABLE terminology.value_set_snapshot ADD CONSTRAINT value_set_snapshot_value_set_fk FOREIGN KEY (value_set) REFERENCES terminology.value_set(id) ON UPDATE CASCADE;

ALTER TABLE terminology.map_set DROP CONSTRAINT map_set_source_value_set_fk;
ALTER TABLE terminology.map_set ADD CONSTRAINT map_set_source_value_set_fk FOREIGN KEY (source_value_set) REFERENCES terminology.value_set(id) ON UPDATE CASCADE;

ALTER TABLE terminology.map_set DROP CONSTRAINT map_set_target_value_set_fk;
ALTER TABLE terminology.map_set ADD CONSTRAINT map_set_target_value_set_fk FOREIGN KEY (target_value_set) REFERENCES terminology.value_set(id) ON UPDATE CASCADE;
--rollback select 1;

--changeset kodality:code_system_fix_fk-1
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

ALTER TABLE terminology.map_set_association DROP CONSTRAINT ms_association_source_cs_id_fk;
ALTER TABLE terminology.map_set_association ADD CONSTRAINT ms_association_source_cs_id_fk FOREIGN KEY (source_code_system) REFERENCES terminology.code_system(id) ON UPDATE CASCADE;

ALTER TABLE terminology.map_set_association DROP CONSTRAINT ms_association_target_cs_id_fk;
ALTER TABLE terminology.map_set_association ADD CONSTRAINT ms_association_target_cs_id_fk FOREIGN KEY (target_code_system) REFERENCES terminology.code_system(id) ON UPDATE CASCADE;

ALTER TABLE terminology.code_system_supplement DROP CONSTRAINT code_system_supplement_code_system_fk;
ALTER TABLE terminology.code_system_supplement ADD CONSTRAINT code_system_supplement_code_system_fk FOREIGN KEY (code_system) REFERENCES terminology.code_system(id) ON UPDATE CASCADE;

ALTER TABLE terminology.naming_system DROP CONSTRAINT naming_system_code_system_fk;
ALTER TABLE terminology.naming_system ADD CONSTRAINT naming_system_code_system_fk FOREIGN KEY (code_system) REFERENCES terminology.code_system(id) ON UPDATE CASCADE;
--rollback select 1;
