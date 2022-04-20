create or replace function core.jsonb_field_text
(
    p_names jsonb,
    p_key text
) returns text
as
$body$
begin
    return p_names ->> p_key;
end;
$body$
language plpgsql immutable
