drop function if exists terminology.value_set_expand(p_value_set_version_id bigint);

create or replace function terminology.value_set_expand(
    p_value_set_version_id bigint
)
    returns table (
        concept                 jsonb,
        display                 jsonb,
        additional_designations jsonb,
        order_number            smallint,
        enumerated              boolean
    )
    language sql
as
$function$
with rules as (
  select r.id rule_id, r."type", r.code_system, r.code_system_version_id, r.filters, r.concepts, cs.uri uri, bcs.uri base_uri, r.value_set_version_id
    from terminology.value_set_version_rule_set rs
         inner join terminology.value_set_version v on v.id = rs.value_set_version_id and v.sys_status = 'A'
         inner join terminology.value_set_version_rule r on r.rule_set_id = rs.id and r.sys_status = 'A'
                and ((r.code_system is not null or r.value_set_version_id is not null) 
                      and r.code_system not in ('snomed-ct','loinc','ucum'))
         left outer join terminology.code_system cs on cs.sys_status = 'A' and cs.id = r.code_system    
         left outer join terminology.code_system bcs on bcs.sys_status = 'A' and bcs.id = cs.base_code_system
   where v.id = p_value_set_version_id and rs.sys_status = 'A'
), 
exact_concepts as ( 
  with t as (
	  select r.*, jsonb_array_elements(r.concepts) concept
      from rules r
  ) 
  select t.rule_id, t."type", t.code_system, t.code_system_version_id, 
         t.concept -> 'concept' ->> 'code' csev_code, 
         jsonb_build_object('conceptVersionId', csev.id,
                            'code', t.concept -> 'concept' ->> 'code', 
                            'codeSystem', t.code_system, 'codeSystemUri', t.uri, 'baseCodeSystemUri', t.base_uri) obj,
         (t.concept -> 'display') display, 
         (t.concept -> 'additionalDesignations') additional_designations, 
         (t.concept -> 'orderNumber')::smallint order_number
    from t 
         left outer join terminology.code_system_entity_version csev on t.code_system = csev.code_system
                and (t.concept -> 'concept' ->> 'code') = csev.code
         left outer join terminology.entity_version_code_system_version_membership evcsvm 
                 on evcsvm.sys_status = 'A' and evcsvm.code_system_version_id = t.code_system_version_id 
                and csev.id = evcsvm.code_system_entity_version_id
),
expressions as (
  -- list the all unique filters
  with recursive t as (
    with f as (
		  select r.*, jsonb_array_elements(r.filters) filter_, jsonb_array_length(r.filters) fcnt
	      from rules r
	     where r.filters is not null 
    ) select f.*,
             row_number() over () rn
        from f
  ), 
  -- list of the all recursive values expressed with is-a operator
  r as (
		select csev.code_system, csa.source_code_system_entity_version_id csev_id, t.rule_id
		  from t 
		       inner join terminology.code_system_entity_version csev 
		               on csev.code_system = t.code_system and csev.sys_status ='A'
		              and csev.code = (t.filter_ ->> 'value')::text 
		       inner join terminology.code_system_association csa 
		               on csa.association_type = (t.filter_ ->> 'operator') and csa.sys_status  ='A' 
		              and csev.id = csa.target_code_system_entity_version_id
	         left outer join terminology.entity_version_code_system_version_membership evcsvm 
	                 on evcsvm.sys_status = 'A' and evcsvm.code_system_version_id = t.code_system_version_id 
	                and csev.id = evcsvm.code_system_entity_version_id
		 where (t.filter_ ->> 'operator')::text = 'is-a'
	  union 
	  select r.code_system, csa1.source_code_system_entity_version_id csev_id, rule_id
	    from r, terminology.code_system_association csa1
	   where r.code_system=csa1.code_system
	     and r.csev_id = csa1.target_code_system_entity_version_id
	     and csa1.sys_status = 'A'
  ), 
  c as (
	  select t.rule_id, t."type", t.code_system, t.code_system_version_id, csev.id csev_id, 
	         csev.code csev_code, 
	         jsonb_build_object('conceptVersionId', csev.id, 
	                            'code', csev.code, 'codeSystem', t.code_system, 
	                            'codeSystemUri', t.uri, 'baseCodeSystemUri', t.base_uri) obj
	    from rules t
	         left outer join terminology.code_system_entity_version csev 
	                 on t.code_system = csev.code_system and csev.sys_status='A'
	         left outer join terminology.entity_version_code_system_version_membership evcsvm 
	                 on evcsvm.sys_status = 'A' and evcsvm.code_system_version_id = t.code_system_version_id 
	                and csev.id = evcsvm.code_system_entity_version_id
	   where t.filters is not null      
   )
   -- concepts that match by exact code or regexp applied to code 
   select c.*, t.rn, t.fcnt from c, t  
    where t.code_system = c.code_system
      and t.rule_id = c.rule_id
      and ((t.filter_ -> 'property' ->> 'name')::text = 'code' and 
           (c.csev_code = (t.filter_ ->> 'value')::text or 
           regexp_match(c.csev_code, (t.filter_ ->> 'value')::text||'$') is not null))
   union     
   -- concepts that match by properties 
   select c.*, t.rn, t.fcnt from c, t 
    where t.code_system = c.code_system
      and t.rule_id = c.rule_id
      and (t.filter_ ->> 'operator')::text <> 'is-a' 
      and exists (select 1 from terminology.entity_property_value epv, terminology.entity_property ep 
                  where epv.sys_status = 'A' and c.csev_id = epv.code_system_entity_version_id
                    and ep.sys_status = 'A' and ep.id = epv.entity_property_id
                    and (t.filter_ -> 'property' ->> 'name')::text = ep.name 
                    and ((t.filter_ ->> 'value') is null or (t.filter_ -> 'value') = epv.value::jsonb or 
                         (t.filter_ ->> 'value')::text = (epv.value ->> 'code')::text)
                 ) 
   union 
   -- concepts that match by designations 
   select c.*, t.rn, t.fcnt from c, t 
    where t.code_system = c.code_system
      and t.rule_id = c.rule_id
      and exists (select 1 from terminology.designation d, terminology.entity_property ep 
                  where d.sys_status = 'A' and c.csev_id = d.code_system_entity_version_id
                    and ep.sys_status = 'A' and ep.id = d.designation_type_id
                    and (t.filter_ -> 'property' ->> 'name')::text = ep.name  
                    and ((t.filter_ ->> 'value')::text = d.name))
   union 
   -- all recursive concepts calculated before
   select c.*, t.rn, t.fcnt from c, t, r 
    where t.code_system = c.code_system
      and t.rule_id = c.rule_id
      and t.code_system = r.code_system
      and t.rule_id = r.rule_id
      and c.csev_id = r.csev_id
),
expression_concepts as (
	select rule_id, type, csev_code, obj, fcnt, count(*)
	  from expressions
	 group by rule_id, type, csev_code, obj, fcnt
	having fcnt = count(*)   
),
cs as (
  select t.rule_id, t."type", t.code_system, t.code_system_version_id, csev.id csev_id, csev.code csev_code, 
         jsonb_build_object('conceptVersionId', csev.id, 'code', csev.code, 'codeSystem', t.code_system, 'codeSystemUri', t.uri, 'baseCodeSystemUri', t.base_uri) obj
    from rules t, 
         terminology.code_system_entity_version csev,
         terminology.entity_version_code_system_version_membership evcsvm 
   where t.code_system = csev.code_system
     and evcsvm.sys_status = 'A' 
     and evcsvm.code_system_version_id = t.code_system_version_id 
     and csev.id = evcsvm.code_system_entity_version_id
     and t.filters is null and t.concepts is null 
),
vs as (
  select s.* from rules t, lateral terminology.value_set_expand(t.value_set_version_id) s
   where t.value_set_version_id is not null 
),
codes as (
	select csev_code, obj
	  from cs
	 where type='include'
  union
	select concept ->> 'code', concept
	  from vs  
  union
	 select csev_code, obj
	  from expression_concepts
	 where type='include'
	union
	select csev_code, obj 
	  from exact_concepts
	  where type='include' 
	except   
	select csev_code, obj 
	  from cs
	 where type='exclude'
	except
	select csev_code, obj 
	  from expression_concepts
	 where type='exclude'
	except
	select csev_code, obj 
	  from exact_concepts
	  where type='exclude' 
)
select t.obj, ec.display, ec.additional_designations, ec.order_number, 
       case when ec.csev_code is not null then true else false end
  from codes t left outer join exact_concepts ec on t.csev_code = ec.csev_code
 order by ec.order_number;

$function$
;
