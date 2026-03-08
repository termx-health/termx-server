package org.termx.core.acl;

import com.kodality.commons.exception.ForbiddenException;
import com.kodality.commons.exception.NotFoundException;
import jakarta.inject.Singleton;
import java.util.List;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class AclService {
  private final AclRepository aclRepository;

  public void init(Long sId, String tenant) {
    aclRepository.init(sId, tenant);
  }

  public void validate(Long sId, String tenant, String access) {
    if (sId == null) {
      return;
    }
    if (!aclRepository.check(sId, tenant, access)) {
      if (!access.equals("view") && aclRepository.check(sId, tenant, "view")) {
        throw new ForbiddenException(sId + access);
      }
      throw new NotFoundException("object", sId);
    }
  }

  public void permit(Long sId, String tenant, String access) {
    aclRepository.permit(sId, tenant, access);
  }

  public void revoke(Long sId, String tenant) {
    aclRepository.revoke(sId, tenant);
  }

  public void copy(Long fromSId, Long toSId) {
    aclRepository.copy(fromSId, toSId);
  }

  public List<String> getTenants(Long sId, String access) {
    return aclRepository.getTenants(sId, access);
  }
}
