package com.kodality.termx.observationdefinition.observationdefinition.member;

import com.kodality.termx.observationdefinition.observationdefinition.ObservationDefinitionRepository;
import com.kodality.termx.observationdefintion.ObservationDefinitionMember;
import java.util.List;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Singleton
@RequiredArgsConstructor
public class ObservationDefinitionMemberService {
  private final ObservationDefinitionMemberRepository repository;
  private final ObservationDefinitionRepository observationDefinitionRepository;

  @Transactional
  public void save(List<ObservationDefinitionMember> members, Long observationDefinitionId) {
    repository.retain(members, observationDefinitionId);
    if (members != null) {
      members.forEach(member -> repository.save(member, observationDefinitionId));
    }
  }

  public List<ObservationDefinitionMember> load(Long observationDefinitionId) {
    return repository.load(observationDefinitionId).stream().map(this::decorate).toList();
  }

  private ObservationDefinitionMember decorate(ObservationDefinitionMember member) {
    member.setItem(observationDefinitionRepository.load(member.getItem().getId()));
    return member;
  }
}
