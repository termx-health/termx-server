drop function if exists sys.cancel_implementation_guide(p_ig text);

create or replace function sys.cancel_implementation_guide(p_ig text)
    returns void
    language sql
as
$function$
update sys.implementation_guide_version
   set sys_status = 'D'
 where sys_status <> 'D'
   and implementation_guide = p_ig;

update sys.implementation_guide
   set sys_status = 'D'
 where sys_status <> 'D'
   and id = p_ig;

$function$
;
