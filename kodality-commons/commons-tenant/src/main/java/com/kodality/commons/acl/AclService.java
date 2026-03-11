package com.kodality.commons.acl;

import com.kodality.commons.exception.ForbiddenException;
import com.kodality.commons.exception.NotFoundException;
import java.util.List;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class AclService {
  private final AclRepository aclRepository;

  public void init(Long sid, String tenant) {
    aclRepository.init(sid, tenant);
  }

  public void validate(Long sid, String tenant, String access) {
    if (sid != null && !aclRepository.check(sid, tenant, access)) {
      if (access.equals(AclAccess.view) || !aclRepository.check(sid, tenant, AclAccess.view)) {
        throw new NotFoundException("object", sid);
      }
      throw new ForbiddenException(sid + ":" + access);
    }
  }

  public void permit(Long sid, String tenant, String access) {
    aclRepository.permit(sid, tenant, access);
  }

  public void revoke(Long sid, String tenant) {
    aclRepository.revoke(sid, tenant);
  }

  public void copy(Long fromSid, Long toSid) {
    aclRepository.copy(fromSid, toSid);
  }

  public List<String> getTenants(Long sid, String access) {
    return aclRepository.getTenants(sid, access);
  }
}
