drop function if exists terminology.refresh_concept_closure();

create or replace function terminology.refresh_concept_closure()
    returns void
    security definer
as $$
begin
    refresh materialized view terminology.concept_closure;
    return;
end;
$$ language plpgsql;
