create or replace function core.sequence_nextval(
  l_sequence_id          IN bigint,
  l_curdate              IN timestamp DEFAULT now()
) returns varchar language plpgsql volatile as 
$body$
declare
  l_sequence core.sys_sequence;
  s_str varchar;
  l_oluv text;
  l_nluv text;
  l_luv_period timestamp;
  l_numstr varchar;
  l_sequence_type varchar;
  rc bigint;
begin
  if l_curdate is null then
    l_curdate := LOCALTIMESTAMP;
  end if;

  select * into l_sequence from core.sys_sequence where id = l_sequence_id;

  s_str := l_sequence.pattern;
  s_str := replace(s_str, '[YY]', to_char(l_curdate,'YY'));
  s_str := replace(s_str, '[YYYY]', to_char(l_curdate,'YYYY'));
  s_str := replace(s_str, '[MM]', to_char(l_curdate,'MM'));
  s_str := replace(s_str, '[DD]', to_char(l_curdate,'DD'));

  -- find numeric string for replacement (find for [NN..NN])
  case when (strpos(s_str, '[N') > 0) then
         l_sequence_type := 'NUMBER';
         l_numstr := substr(s_str,strpos(s_str,'[N'), strpos(s_str,'N]')-strpos(s_str,'[N')+2);
       when (strpos(s_str, '[A') > 0) then
         l_sequence_type := 'ALPHABET';
         l_numstr := substr(s_str,strpos(s_str,'[A'), strpos(s_str,'A]')-strpos(s_str,'[A')+2);
       when (strpos(s_str, '[0') > 0) then
         l_sequence_type := 'ALPHANUMERIC';
         l_numstr := substr(s_str,strpos(s_str,'[0'), strpos(s_str,'0]')-strpos(s_str,'[0')+2);
  end case;
    
  -- find period of the sequence
  case when (l_sequence.restart='daily') then l_luv_period := date_trunc('day',l_curdate);
       when (l_sequence.restart='monthly') then l_luv_period := date_trunc('month',l_curdate);
       when (l_sequence.restart='yearly') then l_luv_period := date_trunc('year',l_curdate);
       else l_luv_period := null;
   end case;
 -- RAISE NOTICE 'Sequence: format=%, restart=%, start_from_num=%, numstr=%, period=%', l_pattern, l_seq_restart, l_start_from, l_numstr, l_luv_period;

  -- find last values and create new LUV record if not exists
  begin
     select luv into l_oluv 
       from core.sys_sequence_luv
      where sequence_id = l_sequence.id 
        and coalesce(period, LOCALTIMESTAMP) = coalesce(l_luv_period, LOCALTIMESTAMP);
     if NOT FOUND THEN
       l_oluv := l_sequence.start_from;
       begin
         insert into core.sys_sequence_luv (sequence_id, period, luv, sys_modify_time)
           values (l_sequence.id, l_luv_period, l_oluv, LOCALTIMESTAMP);
         EXCEPTION WHEN others THEN raise notice '% %', SQLSTATE, SQLERRM;         
       end;
    end if;
  end;


  -- increase numeric value of sequence
  loop
    RAISE NOTICE 'Working in loop. oluv=%', l_oluv;
    l_nluv := core.sequence_nextvala(l_oluv,length(l_numstr)-2,l_sequence_type);
    update core.sys_sequence_luv
       set luv = l_nluv, sys_modify_time = LOCALTIMESTAMP
     where sequence_id = l_sequence.id 
       and COALESCE(period, LOCALTIMESTAMP) = COALESCE(l_luv_period, LOCALTIMESTAMP)
       and luv = l_oluv; --  and luv = l_oluv is very important criterium
    
    GET DIAGNOSTICS rc = ROW_COUNT; 
    exit when rc=1;
    -- if it fail (it mean any other user asked and committed transaction) then repeat operation
    l_oluv := l_nluv;
  end loop;

  if (length(l_numstr) > 0) then
    if (l_sequence_type = 'NUMBER') then
      -- add leading zeros to string
      s_str := replace(s_str, l_numstr,
                      -- if l_nluv have smaller rank than format then fill absent ranks positions with zeros
                      substr(rpad('*', length(l_numstr) - length(cast(l_nluv as varchar)) -  1, '0'), 2)
                      || l_nluv);
    else
      s_str := replace(s_str, l_numstr, l_nluv);
    end if;
  end if;

  --commit;

  return s_str;

exception
  when no_data_found then
    --ROLLBACK;
    raise exception 'Error on sequence generation. Sequence "%" does not exists.',in_sequence_id;
    RETURN null;
  when others then
    --ROLLBACK;
    raise exception '% %', SQLSTATE, SQLERRM;
    RETURN null;
end;
$body$;
