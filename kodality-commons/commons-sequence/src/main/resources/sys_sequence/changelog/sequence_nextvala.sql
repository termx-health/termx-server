create or replace function core.sequence_nextvala(
  in_luv           IN text,
  in_length        IN int,
  in_sequence_type IN varchar
) returns varchar language plpgsql volatile as 
$body$
declare
  l_pos_from_right integer := 1;
  l_char char;
  l_new_char char;
  l_lus text;
BEGIN
  if (in_sequence_type = 'NUMBER') then
    return (in_luv::bigint + 1)::text;
  end if;
  
  l_lus := lpad(in_luv,in_length,'0'::text);
  while (l_pos_from_right > 0) loop
    l_char := substr(l_lus,length(l_lus)-l_pos_from_right + 1,1);
    if (l_char = chr(90)) then -- ascii 90 = Z
      if (in_sequence_type = 'ALPHANUMERIC') then
        l_new_char := '0'; -- ascii 48
      elsif (in_sequence_type = 'ALPHABET') then
        l_new_char := 'A';
      end if;
      -- in case the maximum value is reached: start from the beginning
      if ((in_length-l_pos_from_right) = 0) then
        return lpad(l_new_char,in_length,'0');
      end if;
      l_lus := substr(l_lus,1,length(l_lus)-l_pos_from_right)||l_new_char||substr(l_lus,length(l_lus)-l_pos_from_right + 2);
      l_pos_from_right := l_pos_from_right + 1;
    else
      if (in_sequence_type = 'ALPHANUMERIC' and ascii(l_char) = 57) then
        l_new_char := 'A';-- ascii 65
      elsif (in_sequence_type = 'ALPHABET' and ascii(l_char) < 57) then
        l_new_char := 'A';-- ascii 65
      else
        l_new_char := chr(ascii(l_char) + 1);
      end if;
      l_lus := substr(l_lus,1,length(l_lus)-l_pos_from_right)||l_new_char||substr(l_lus,length(l_lus)-l_pos_from_right + 2);
      l_pos_from_right := 0;
    end if;
  end loop;

  return l_lus;
exception when OTHERS then  
  raise exception '%', SQLERRM;
end;
$body$;